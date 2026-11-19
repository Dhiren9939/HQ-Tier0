package me.dhiren9939.backend.access.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import me.dhiren9939.backend.access.infrastructure.persistence.repositories.OutboxStore;
import me.dhiren9939.backend.access.infrastructure.serialization.IntegrationEventSerializationException;
import me.dhiren9939.backend.access.infrastructure.serialization.IntegrationEventSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes unpublished outbox rows to the projection super stream, at least once, and halts on the first row it
 * cannot publish.
 *
 * <p>Each tick runs in one transaction: lock up to {@code batch-size} pending rows (oldest first,
 * {@code FOR UPDATE SKIP LOCKED}), build each body with {@link IntegrationEventSerializer#withEmittedAt} using one
 * {@code emittedAt} for the tick, send them all, then wait for every publisher confirm against one deadline for the
 * whole batch ({@code confirm-timeout}).</p>
 *
 * <ul>
 *   <li><b>All confirmed</b>: every row is stamped with the body that was sent, and the transaction commits.</li>
 *   <li><b>Outage</b> (the stream cannot be declared, a send fails, the deadline passes, or a confirm fails for a
 *       reason that is not about the message): the transaction rolls back, nothing is stamped or counted, and the
 *       next tick polls the same rows again. Messages already confirmed are then sent twice, which consumers absorb:
 *       every event is a full snapshot with the key's {@code version}.</li>
 *   <li><b>Refused</b> (the broker answered that it will not take a message) or <b>unpublishable</b> (a stored payload
 *       breaks the contract): the confirmed rows are stamped, the failing rows get {@code attempts + 1} and
 *       {@code last_error}, the transaction commits and the relay halts. Nothing is retried: a row that cannot be
 *       published is a contract or configuration failure that a person must fix. A restart clears the halt and starts
 *       again from that row, which is first in order.</li>
 * </ul>
 */
@Slf4j
public class OutboxRelay {
    private final OutboxStore store;
    private final ProjectionStream stream;
    private final IntegrationEventSerializer serializer;
    private final OutboxRelayProperties properties;
    private final Clock clock;
    private final TransactionOperations transactions;

    private volatile Halt halt;

    public OutboxRelay(OutboxStore store, ProjectionStream stream, IntegrationEventSerializer serializer,
                       OutboxRelayProperties properties, Clock clock, TransactionOperations transactions) {
        this.store = store;
        this.stream = stream;
        this.serializer = serializer;
        this.properties = properties;
        this.clock = clock;
        this.transactions = transactions;
    }

    /** Why the relay stopped: the first row it could not publish. */
    public record Halt(UUID eventId, String eventType, String error) {
    }

    /** Present once the relay has halted; it stays halted until the application restarts. */
    public Optional<Halt> halt() {
        return Optional.ofNullable(halt);
    }

    /** One tick, unless the relay has halted. */
    @Scheduled(fixedDelayString = "${access.outbox.relay.poll-interval:PT2S}")
    public void tick() {
        if (halt != null)
            return;
        Halt stuck = transactions.execute(this::relayBatch);
        if (stuck != null) {
            halt = stuck;
            log.error("Outbox relay halted at event {} ({}): {}. Fix the cause and restart to resume.",
                    stuck.eventId(), stuck.eventType(), stuck.error());
        }
    }

    /** Returns the halt to apply after commit, or null. */
    private Halt relayBatch(TransactionStatus status) {
        try {
            stream.declare();
        } catch (RuntimeException e) {
            log.warn("Outbox relay could not declare the stream; repolling next tick: {}", e.toString());
            return null;
        }

        List<EventOutboxEntity> rows = store.lockPending(properties.batchSize());
        if (rows.isEmpty())
            return null;
        // PostgreSQL keeps microseconds; truncating keeps the emitted_at column and the JSON emittedAt identical.
        Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);

        List<Pending> sendable = new ArrayList<>();
        Failure unpublishable = null;
        for (EventOutboxEntity row : rows) {
            try {
                sendable.add(new Pending(row, serializer.withEmittedAt(row.getPayload(), at)));
            } catch (IntegrationEventSerializationException e) {
                unpublishable = new Failure(row, "Unpublishable: " + e.getMessage());
                break;
            }
        }

        long deadline = System.nanoTime() + properties.confirmTimeout().toNanos();
        List<CompletableFuture<Boolean>> confirms = new ArrayList<>();
        try {
            for (Pending pending : sendable)
                confirms.add(stream.send(pending.row(), pending.body()));
        } catch (RuntimeException e) {
            return outage(status, "a send failed: " + e);
        }

        List<Pending> confirmed = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (int i = 0; i < sendable.size(); i++) {
            Pending pending = sendable.get(i);
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                if (Boolean.TRUE.equals(confirms.get(i).get(remaining, TimeUnit.NANOSECONDS)))
                    confirmed.add(pending);
                else
                    failures.add(new Failure(pending.row(), "Broker did not confirm the message"));
            } catch (TimeoutException e) {
                return outage(status, "publish confirms did not arrive within " + properties.confirmTimeout());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return outage(status, "interrupted while waiting for publish confirms");
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof ProjectionRefusedException refused))
                    return outage(status, "a publish was not confirmed: " + e.getCause());
                failures.add(new Failure(pending.row(), refused.getMessage()));
            }
        }
        if (unpublishable != null)
            failures.add(unpublishable);

        for (Pending pending : confirmed)
            store.markEmitted(pending.row(), at, pending.body());
        if (failures.isEmpty())
            return null;

        for (Failure failure : failures)
            store.recordFailure(failure.row(), failure.error());
        Failure first = failures.getFirst();
        return new Halt(first.row().getEventId(), first.row().getEventType(), first.error());
    }

    private static Halt outage(TransactionStatus status, String reason) {
        status.setRollbackOnly();
        log.warn("Outbox relay cancelled its batch; repolling next tick: {}", reason);
        return null;
    }

    private record Pending(EventOutboxEntity row, String body) {
    }

    private record Failure(EventOutboxEntity row, String error) {
    }
}
