package me.dhiren9939.backend.integration;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import me.dhiren9939.backend.access.infrastructure.persistence.repositories.OutboxStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** The outbox queries and changes the relay relies on, against real PostgreSQL. */
class OutboxStoreIntegrationTest extends AccessIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-15T12:00:00Z");

    @Autowired OutboxStore store;
    @Autowired PlatformTransactionManager transactionManager;

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private UUID issueAt(Instant at, String name) {
        clock.pinTo(at);
        return issueActive(UUID.randomUUID(), name, Lifetime.NEVER, ingestionWrite()).key().apiKeyId();
    }

    @Test
    @DisplayName("pending rows come oldest first by occurred_at, up to the limit, and emitted rows are left out")
    void pendingOrderAndLimit() {
        UUID later = issueAt(T0.plusSeconds(20), "later");
        UUID earliest = issueAt(T0, "earliest");
        UUID middle = issueAt(T0.plusSeconds(10), "middle");

        List<UUID> firstTwo = inTransaction(() -> store.lockPending(2).stream().map(EventOutboxEntity::getApiKeyId).toList());
        assertThat(firstTwo).containsExactly(earliest, middle);

        inTransaction(() -> {
            store.markEmitted(store.lockPending(1), T0.plusSeconds(30));
            return null;
        });

        List<UUID> rest = inTransaction(() -> store.lockPending(10).stream().map(EventOutboxEntity::getApiKeyId).toList());
        assertThat(rest).containsExactly(middle, later);
        assertThat(outbox().stream().filter(row -> row.emittedAt() != null).map(OutboxRow::apiKeyId))
                .containsExactly(earliest);
    }

    @Test
    @DisplayName("an appended row's columns and its payload agree, and the payload has no emittedAt yet")
    void appendKeepsColumnsAndPayloadInStep() {
        UUID key = issueAt(T0, "k");

        OutboxRow row = outbox().getFirst();

        assertThat(row.payload().get("eventId").asString()).isEqualTo(row.eventId().toString());
        assertThat(row.payload().get("eventType").asString()).isEqualTo(row.eventType());
        assertThat(row.payload().get("apiKeyId").asString()).isEqualTo(key.toString());
        assertThat(row.payload().get("version").asLong()).isEqualTo(row.version());
        assertThat(Instant.parse(row.payload().get("occurredAt").asString())).isEqualTo(T0);
        assertThat(row.payload().has("emittedAt")).isFalse();
    }

    @Test
    @DisplayName("marking a row emitted stamps the column and the payload with the same time")
    void markEmittedStampsColumnAndPayload() {
        issueAt(T0, "k");
        Instant at = T0.plusSeconds(30);

        inTransaction(() -> {
            store.markEmitted(store.lockPending(1), at);
            return null;
        });

        OutboxRow row = outbox().getFirst();
        assertThat(row.emittedAt()).isNotNull();
        assertThat(Instant.parse(row.payload().get("emittedAt").asString())).isEqualTo(at);
        assertThat(jdbc.queryForObject("select emitted_at = (payload->>'emittedAt')::timestamptz from access.outbox",
                Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("a refused attempt is counted and kept on the row")
    void recordFailurePersists() {
        issueAt(T0, "k");

        int attempts = inTransaction(() -> store.recordFailure(store.lockPending(1).getFirst(), "refused"));

        assertThat(attempts).isEqualTo(1);
        assertThat(jdbc.queryForObject("select attempts from access.outbox", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select last_error from access.outbox", String.class)).isEqualTo("refused");
    }

    @Test
    @DisplayName("dead-lettering moves the row out of the outbox in one transaction")
    void deadLetterMovesTheRow() {
        UUID key = issueAt(T0, "doomed");

        inTransaction(() -> {
            EventOutboxEntity row = store.lockPending(1).getFirst();
            store.recordFailure(row, "refused");
            store.deadLetter(row, "gave up", T0.plusSeconds(5));
            return null;
        });

        assertThat(count("access.outbox")).isZero();
        assertThat(jdbc.queryForMap("select api_key_id, change_version, attempts, last_error, dead_at, "
                + "payload->>'accessSecretHash' as access_secret_hash from access.outbox_dead_letter"))
                .containsEntry("api_key_id", key)
                .containsEntry("change_version", 1L)
                .containsEntry("attempts", 1)
                .containsEntry("last_error", "gave up")
                .satisfies(row -> assertThat(row.get("access_secret_hash")).isNotNull());
    }

    @Test
    @DisplayName("rows another transaction has locked are skipped, not waited for")
    void lockedRowsAreSkipped() throws Exception {
        UUID first = issueAt(T0, "first");
        UUID second = issueAt(T0.plusSeconds(1), "second");

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            Future<List<UUID>> holder = other.submit(() -> inTransaction(() -> {
                List<UUID> held = store.lockPending(1).stream().map(EventOutboxEntity::getApiKeyId).toList();
                locked.countDown();
                await(release);
                return held;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            List<UUID> seen = inTransaction(() -> store.lockPending(10).stream().map(EventOutboxEntity::getApiKeyId).toList());

            release.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).containsExactly(first);
            assertThat(seen).containsExactly(second);
        } finally {
            release.countDown();
            other.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
