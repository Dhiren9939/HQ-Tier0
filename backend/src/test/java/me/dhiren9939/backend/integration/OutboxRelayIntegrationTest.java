package me.dhiren9939.backend.integration;

import com.rabbitmq.stream.Address;
import com.rabbitmq.stream.Consumer;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.OffsetSpecification;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyUseCase;
import me.dhiren9939.backend.access.domain.aggregates.apikey.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.EnvironmentBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The relay end to end: real PostgreSQL, a real RabbitMQ with the stream plugin, and the scheduled relay running.
 * The context is closed afterwards so its relay does not keep stamping rows while other test classes run.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "access.outbox.relay.poll-interval=PT0.2S",
        "access.outbox.relay.confirm-timeout=PT2S",
        "access.outbox.relay.backoff-base=PT0.5S",
        "access.outbox.relay.backoff-max=PT2S"})
@Import({TestClockConfig.class, OutboxRelayIntegrationTest.StreamAddress.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxRelayIntegrationTest {

    private static final String STREAM = "apikey.projections";
    private static final int STREAM_PORT = 5552;

    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management")
            .withCopyToContainer(Transferable.of("[rabbitmq_management,rabbitmq_stream]."), "/etc/rabbitmq/enabled_plugins")
            .withExposedPorts(5672, 15672, STREAM_PORT);

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        var postgres = PostgresIntegrationTest.POSTGRES; // starts the shared container
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("access.outbox.relay.enabled", () -> "true");
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.stream.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.stream.port", () -> RABBIT.getMappedPort(STREAM_PORT));
    }

    /** The broker advertises its container hostname; always connect to the mapped port on the host instead. */
    @TestConfiguration(proxyBeanMethods = false)
    static class StreamAddress {
        @Bean
        EnvironmentBuilderCustomizer mappedStreamAddress() {
            return builder -> builder.addressResolver(
                    address -> new Address(RABBIT.getHost(), RABBIT.getMappedPort(STREAM_PORT)));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired Environment streams;
    @Autowired IssueApiKeyUseCase issueApiKey;
    @Autowired UpdateApiKeyUseCase updateApiKey;
    @Autowired DeleteApiKeyUseCase deleteApiKey;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE access.outbox, access.outbox_dead_letter, access.api_key_permissions, access.api_keys");
        clock.reset();
    }

    private UUID issue(UUID owner, String name) {
        return issueApiKey.handle(new IssueApiKeyCommand(owner, name, Lifetime.NEVER,
                Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE))).key().apiKeyId();
    }

    private void rename(UUID owner, UUID apiKeyId, String name) {
        updateApiKey.handle(new UpdateApiKeyCommand(owner, apiKeyId, name, null, null));
    }

    private long pending() {
        Long n = jdbc.queryForObject("select count(*) from access.outbox where emitted_at is null", Long.class);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("every event reaches the super stream once confirmed, each key on one partition in version order")
    void publishesEveryEventInOrderPerKey() {
        UUID owner = UUID.randomUUID();
        Map<UUID, Integer> eventsPerKey = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            UUID key = issue(owner, "key-" + i);
            rename(owner, key, "renamed-" + i);
            rename(owner, key, "again-" + i);
            eventsPerKey.put(key, 3);
            if (i % 2 == 0) {
                deleteApiKey.handle(new DeleteApiKeyCommand(owner, key));
                eventsPerKey.put(key, 4);
            }
        }
        int total = eventsPerKey.values().stream().mapToInt(Integer::intValue).sum();

        await(Duration.ofSeconds(30), () -> pending() == 0);
        assertThat(jdbc.queryForObject("select count(*) from access.outbox where emitted_at is not null", Long.class))
                .isEqualTo(total);

        List<Received> received = readAllPartitions(total, eventsPerKey.keySet());

        Map<UUID, Set<Integer>> partitionsPerKey = new HashMap<>();
        Map<UUID, List<Long>> versionsPerKey = new HashMap<>();
        for (Received r : received) {
            partitionsPerKey.computeIfAbsent(r.apiKeyId(), k -> new HashSet<>()).add(r.partition());
            versionsPerKey.computeIfAbsent(r.apiKeyId(), k -> new ArrayList<>()).add(r.version());
        }
        eventsPerKey.forEach((key, count) -> {
            assertThat(partitionsPerKey.get(key)).as("one partition per key").hasSize(1);
            List<Long> versions = versionsPerKey.get(key);
            assertThat(versions).as("versions of %s in partition order", key).hasSize(count).isSorted();
        });
        assertThat(received).allSatisfy(r -> assertThat(r.body().get("accessSecretHash").isString()).isTrue());
        assertThat(received.stream().map(Received::partition).distinct()).as("keys spread over both partitions")
                .hasSize(2);
    }

    @Test
    @DisplayName("while the broker is unreachable rows stay pending; they drain once it is back")
    void survivesABrokerOutage() {
        UUID owner = UUID.randomUUID();
        issue(owner, "before");
        await(Duration.ofSeconds(30), () -> pending() == 0);

        RABBIT.getDockerClient().pauseContainerCmd(RABBIT.getContainerId()).exec();
        try {
            UUID key = issue(owner, "during");
            rename(owner, key, "during-renamed");
            sleep(Duration.ofSeconds(5));
            assertThat(pending()).as("nothing is stamped without a broker confirm").isEqualTo(2);
            assertThat(jdbc.queryForObject("select coalesce(max(attempts), 0) from access.outbox", Integer.class))
                    .as("an outage never counts against a row").isZero();
        } finally {
            RABBIT.getDockerClient().unpauseContainerCmd(RABBIT.getContainerId()).exec();
        }

        await(Duration.ofSeconds(90), () -> pending() == 0);
        assertThat(jdbc.queryForObject("select count(*) from access.outbox_dead_letter", Long.class)).isZero();
    }

    private record Received(int partition, UUID apiKeyId, long version, JsonNode body) {
    }

    /** Reads both partitions from the start and keeps the messages of the given keys. */
    private List<Received> readAllPartitions(int expected, Set<UUID> keys) {
        Queue<Received> received = new ConcurrentLinkedQueue<>();
        List<Consumer> consumers = new ArrayList<>();
        try {
            for (int partition = 0; partition < 2; partition++) {
                int p = partition;
                consumers.add(streams.consumerBuilder()
                        .stream(STREAM + "-" + p)
                        .offset(OffsetSpecification.first())
                        .messageHandler((context, message) -> {
                            UUID apiKeyId = UUID.fromString(
                                    String.valueOf(message.getApplicationProperties().get("apiKeyId")));
                            if (keys.contains(apiKeyId)) {
                                JsonNode body = json.readTree(message.getBodyAsBinary());
                                received.add(new Received(p, apiKeyId, body.get("version").asLong(), body));
                            }
                        })
                        .build());
            }
            await(Duration.ofSeconds(30), () -> received.size() >= expected);
        } finally {
            consumers.forEach(Consumer::close);
        }
        return new ArrayList<>(received);
    }

    private static void await(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline)
                throw new AssertionError("condition not met within " + timeout);
            sleep(Duration.ofMillis(200));
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
