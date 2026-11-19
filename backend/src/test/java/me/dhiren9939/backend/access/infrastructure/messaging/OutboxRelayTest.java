package me.dhiren9939.backend.access.infrastructure.messaging;

import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import me.dhiren9939.backend.access.infrastructure.persistence.repositories.OutboxStore;
import me.dhiren9939.backend.access.infrastructure.serialization.IntegrationEventSerializer;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyAccessLevel;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyScope;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** The relay's decisions for each outcome of a tick, against a scripted stream and in-memory rows. */
class OutboxRelayTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final IntegrationEventSerializer serializer = new IntegrationEventSerializer(JSON);
    private final FakeStream stream = new FakeStream();
    private final FakeTransactions transactions = new FakeTransactions();
    private final List<EventOutboxEntity> pending = new ArrayList<>();
    private OutboxStore store;

    @BeforeEach
    void setUp() {
        // Real markEmitted / recordFailure on the entities; lockPending serves the in-memory rows.
        store = mock(OutboxStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doAnswer(invocation -> pending.stream().filter(row -> row.getEmittedAt() == null)
                .limit(invocation.<Integer>getArgument(0)).toList())
                .when(store).lockPending(anyInt());
    }

    private OutboxRelay relay(Duration confirmTimeout) {
        return new OutboxRelay(store, stream, serializer,
                new OutboxRelayProperties(true, "apikey.projections", 2, 100, Duration.ofSeconds(2), confirmTimeout),
                Clock.fixed(NOW, ZoneOffset.UTC), transactions);
    }

    private OutboxRelay relay() {
        return relay(Duration.ofSeconds(5));
    }

    private EventOutboxEntity row(int version) {
        UUID eventId = UUID.randomUUID();
        ApiKeyUpdatedIntegrationEvent event = ApiKeyUpdatedIntegrationEvent.builder()
                .apiKeyName("orders").apiKeyId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .version(version).occurredAt(NOW).userId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .accessId("V1_acc").accessSecretHash("hash")
                .permissions(Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE)).createdAt(CREATED_AT).build();
        EventOutboxEntity row = EventOutboxEntity.of(eventId, event, serializer.toJson(eventId, event));
        pending.add(row);
        return row;
    }

    private static void assertUntouched(EventOutboxEntity row) {
        assertThat(row.getEmittedAt()).isNull();
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void confirmedRowsAreSentInOrderAndStampedWithTheBodyThatWasSent() {
        EventOutboxEntity first = row(1), second = row(2), third = row(3);

        OutboxRelay relay = relay();
        relay.tick();

        assertThat(stream.sentRows).containsExactly(first, second, third);
        for (int i = 0; i < 3; i++) {
            EventOutboxEntity row = stream.sentRows.get(i);
            JsonNode body = JSON.readTree(stream.sentBodies.get(i));
            assertThat(body.get("emittedAt").asString()).isEqualTo(NOW.toString());
            assertThat(body.get("eventId").asString()).isEqualTo(row.getEventId().toString());
            assertThat(row.getEmittedAt()).isEqualTo(NOW);
            assertThat(row.getPayload()).as("the stored payload is what was published").isEqualTo(stream.sentBodies.get(i));
            assertThat(row.getAttempts()).isZero();
        }
        assertThat(transactions.last.isRollbackOnly()).isFalse();
        assertThat(relay.halt()).isEmpty();
    }

    @Test
    void emittedAtIsKeptToTheMicrosecondTheDatabaseStores() {
        EventOutboxEntity row = row(1);
        Instant precise = Instant.parse("2026-01-15T12:00:00.123456789Z");

        new OutboxRelay(store, stream, serializer,
                new OutboxRelayProperties(true, "apikey.projections", 2, 100, Duration.ofSeconds(2), Duration.ofSeconds(5)),
                Clock.fixed(precise, ZoneOffset.UTC), transactions).tick();

        assertThat(row.getEmittedAt()).isEqualTo(Instant.parse("2026-01-15T12:00:00.123456Z"));
        assertThat(JSON.readTree(row.getPayload()).get("emittedAt").asString()).isEqualTo("2026-01-15T12:00:00.123456Z");
    }

    @Test
    void anEmptyOutboxSendsNothing() {
        relay().tick();

        assertThat(stream.declared).isEqualTo(1);
        assertThat(stream.sentRows).isEmpty();
    }

    @Test
    void aStreamThatCannotBeDeclaredIsAnOutage() {
        EventOutboxEntity row = row(1);
        stream.declareFailure = new IllegalStateException("broker down");

        OutboxRelay relay = relay();
        relay.tick();

        verify(store, never()).lockPending(anyInt());
        assertThat(stream.sentRows).isEmpty();
        assertUntouched(row);
        assertThat(relay.halt()).isEmpty();
    }

    @Test
    void aSendThatThrowsCancelsTheBatchWithoutCountingAnything() {
        EventOutboxEntity first = row(1), second = row(2);
        stream.answer = row -> {
            if (row == second)
                throw new IllegalStateException("connection lost");
            return CompletableFuture.completedFuture(true);
        };

        OutboxRelay relay = relay();
        relay.tick();

        assertThat(transactions.last.isRollbackOnly()).isTrue();
        assertUntouched(first);
        assertUntouched(second);
        assertThat(relay.halt()).isEmpty();
    }

    @Test
    void aConfirmThatFailsForAnyReasonButARefusalIsAnOutage() {
        EventOutboxEntity first = row(1), second = row(2);
        stream.answer = row -> row == second
                ? CompletableFuture.failedFuture(new IOException("connection reset"))
                : CompletableFuture.completedFuture(true);

        OutboxRelay relay = relay();
        relay.tick();

        assertThat(transactions.last.isRollbackOnly()).isTrue();
        assertUntouched(first);
        assertUntouched(second);
        assertThat(relay.halt()).isEmpty();

        stream.answer = row -> CompletableFuture.completedFuture(true);
        relay.tick();

        assertThat(first.getEmittedAt()).as("the next tick publishes the same rows").isEqualTo(NOW);
        assertThat(second.getEmittedAt()).isEqualTo(NOW);
    }

    @Test
    void theConfirmTimeoutIsOneDeadlineForTheWholeBatch() {
        for (int i = 1; i <= 100; i++)
            row(i);
        stream.answer = row -> new CompletableFuture<>(); // never confirmed

        long start = System.nanoTime();
        relay(Duration.ofMillis(300)).tick();
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(stream.sentRows).hasSize(100);
        assertThat(took).as("one timeout for 100 messages, not one each").isLessThan(Duration.ofSeconds(3));
        assertThat(transactions.last.isRollbackOnly()).isTrue();
        assertThat(pending).allSatisfy(OutboxRelayTest::assertUntouched);
    }

    @Test
    void aRefusalStampsTheConfirmedRowsRecordsTheRefusedOneAndHalts() {
        EventOutboxEntity first = row(1), refused = row(2), third = row(3);
        stream.answer = row -> CompletableFuture.completedFuture(row != refused);

        OutboxRelay relay = relay();
        relay.tick();

        assertThat(transactions.last.isRollbackOnly()).as("the outcome is committed").isFalse();
        assertThat(first.getEmittedAt()).isEqualTo(NOW);
        assertThat(third.getEmittedAt()).as("already sent in the same batch").isEqualTo(NOW);
        assertThat(refused.getEmittedAt()).isNull();
        assertThat(refused.getAttempts()).isEqualTo(1);
        assertThat(refused.getLastError()).isNotBlank();
        assertThat(relay.halt()).hasValueSatisfying(halt -> {
            assertThat(halt.eventId()).isEqualTo(refused.getEventId());
            assertThat(halt.eventType()).isEqualTo(refused.getEventType());
        });

        int declared = stream.declared, sent = stream.sentRows.size();
        relay.tick();
        assertThat(stream.declared).as("a halted relay does nothing").isEqualTo(declared);
        assertThat(stream.sentRows).hasSize(sent);

        stream.answer = row -> CompletableFuture.completedFuture(true);
        relay().tick(); // a restart
        assertThat(refused.getEmittedAt()).as("a restart starts again from the stuck row").isEqualTo(NOW);
        assertThat(refused.getAttempts()).isEqualTo(1);
    }

    @Test
    void aRefusalKeepsTheBrokersReasonOnTheRow() {
        EventOutboxEntity refused = row(1);
        stream.answer = row -> CompletableFuture.failedFuture(new ProjectionRefusedException("code 16: access refused", null));

        OutboxRelay relay = relay();
        relay.tick();

        assertThat(refused.getLastError()).isEqualTo("code 16: access refused");
        assertThat(relay.halt()).hasValueSatisfying(halt -> assertThat(halt.error()).isEqualTo("code 16: access refused"));
    }

    @Test
    void anUnpublishableRowDropsTheBatchAndHaltsWithoutSendingAnything() {
        EventOutboxEntity first = row(1), broken = row(2), after = row(3);
        broken.emitted(null, "{\"not\":\"the contract\"}");

        OutboxRelay relay = relay();
        relay.tick();

        assertThat(stream.sentRows).as("nothing is sent").isEmpty();
        assertThat(transactions.last.isRollbackOnly()).as("the error on the row is committed").isFalse();
        assertUntouched(first);
        assertThat(broken.getAttempts()).isEqualTo(1);
        assertThat(broken.getLastError()).startsWith("Unpublishable");
        assertUntouched(after);
        assertThat(relay.halt()).hasValueSatisfying(halt -> assertThat(halt.eventId()).isEqualTo(broken.getEventId()));
    }

    @Test
    void healthIsDownWithTheStuckEventOnlyOnceHalted() {
        EventOutboxEntity refused = row(1);
        OutboxRelay relay = relay();
        OutboxRelayHealth health = new OutboxRelayHealth(relay);

        assertThat(health.health().getStatus()).isEqualTo(Status.UP);

        stream.answer = row -> CompletableFuture.completedFuture(false);
        relay.tick();

        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.health().getDetails()).containsEntry("eventId", refused.getEventId().toString());
    }

    /** A stream whose answer per row is scripted. */
    private static final class FakeStream implements ProjectionStream {
        final List<EventOutboxEntity> sentRows = new ArrayList<>();
        final List<String> sentBodies = new ArrayList<>();
        Function<EventOutboxEntity, CompletableFuture<Boolean>> answer = row -> CompletableFuture.completedFuture(true);
        RuntimeException declareFailure;
        int declared;

        @Override
        public void declare() {
            if (declareFailure != null)
                throw declareFailure;
            declared++;
        }

        @Override
        public CompletableFuture<Boolean> send(EventOutboxEntity row, String body) {
            sentRows.add(row);
            sentBodies.add(body);
            return answer.apply(row);
        }
    }

    /** Runs the callback directly and keeps its status, so tests can see whether the tick asked for a rollback. */
    private static final class FakeTransactions implements TransactionOperations {
        SimpleTransactionStatus last;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            last = new SimpleTransactionStatus();
            return action.doInTransaction(last);
        }
    }
}
