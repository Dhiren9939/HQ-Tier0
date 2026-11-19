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
import java.time.Duration;
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
 * {@code emittedAt} for the tick, send them all, then wait once for every publisher confirm (all settled) with one timeout for the
 * whole batch ({@code confirm-timeout}).</p>
 *
 * <ul>
 *   <li><b>All confirmed</b>: every row is stamped with the body that was sent, and the transaction commits.</li>
 *   <li><b>Outage</b> (the stream cannot be declared, a send fails, the confirm timeout passes, or a confirm fails for a
 *       reason that is not about the message): the transaction rolls back, nothing is stamped or counted, and the
 *       next tick polls the same rows again. Messages already confirmed are then sent twice, which consumers absorb:
 *       every event is a full snapshot with the key's {@code version}.</li>
 *   <li><b>Unpublishable</b> (a stored payload breaks the contract; found while building the bodies, before
 *       anything is sent): the batch is dropped and nothing is sent. Only that row gets {@code attempts + 1} and
 *       {@code last_error}; the transaction commits and the relay halts.</li>
 *   <li><b>Refused</b> (the broker answered that it will not take a message): the confirmed rows are stamped, the
 *       refused rows get {@code attempts + 1} and {@code last_error}, the transaction commits and the relay halts.</li>
 * </ul>
 *
 * <p>Nothing is retried: a row that cannot be published is a contract or configuration failure that a person must
 * fix. A restart clears the halt and starts again from that row, which is first in order.</p>
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

    /**
     * Why the relay stopped: the first event it could not publish, why, and what happened to the rest of that batch.
     *
     * @param reason why the event could not be published
     * @param batch  what happened to the other events taken in the same tick
     */
    public record Halt(UUID eventId, String eventType, UUID apiKeyId, long version, String reason, String batch) {
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
            log.error("""
                    Outbox relay HALTED: nothing more will be published until the application is restarted.
                      event:   {} ({}) for apiKeyId {} version {}
                      reason:  {}
                      batch:   {}
                      recover: fix the cause, then restart the application; publishing resumes from this event. \
                    The error is also stored in access.outbox.last_error for event_id {}.""",
                    stuck.eventId(), stuck.eventType(), stuck.apiKeyId(), stuck.version(), stuck.reason(),
                    stuck.batch(), stuck.eventId());
        }
    }

    /** Returns the halt to apply after commit, or null. */
    private Halt relayBatch(TransactionStatus status) {
        try {
            stream.declare();
        } catch (RuntimeException e) {
            log.warn("Outbox relay: cannot reach the broker to declare stream {}; no events were taken, retrying in {}. "
                    + "Reason: {}", properties.streamName(), readable(properties.pollInterval()), e.toString());
            return null;
        }

        List<EventOutboxEntity> rows = store.lockPending(properties.batchSize());
        if (rows.isEmpty())
            return null;
        // PostgreSQL keeps microseconds; truncating keeps the emitted_at column and the JSON emittedAt identical.
        Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);

        // Build every body before sending anything. One broken payload drops the whole batch: nothing is sent.
        List<Pending> sendable = new ArrayList<>();
        for (EventOutboxEntity row : rows) {
            try {
                sendable.add(new Pending(row, serializer.withEmittedAt(row.getPayload(), at)));
            } catch (IntegrationEventSerializationException e) {
                store.recordFailure(row, "Unpublishable: " + e.getMessage());
                return halt(row, "its stored payload breaks the event contract: " + e.getMessage(),
                        "dropped: none of the " + rows.size() + " event(s) taken in this tick were sent");
            }
        }

        List<CompletableFuture<Boolean>> confirms = new ArrayList<>();
        try {
            for (Pending pending : sendable)
                confirms.add(stream.send(pending.row(), pending.body()));
        } catch (RuntimeException e) {
            return outage(status, sendable.size(), "sending failed: " + e);
        }

        // Wait once for all the confirms (like Promise.allSettled), at most confirmTimeout in total.
        if (!waitForAll(confirms, properties.confirmTimeout()))
            return outage(status, sendable.size(),
                    "the broker did not confirm every message within " + readable(properties.confirmTimeout()));

        // Every confirm has an answer now, so nothing below waits.
        List<Pending> confirmed = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (int i = 0; i < sendable.size(); i++) {
            Pending pending = sendable.get(i);
            CompletableFuture<Boolean> confirm = confirms.get(i);

            if (confirm.isCompletedExceptionally()) {
                Throwable error = confirm.exceptionNow();
                if (error instanceof ProjectionRefusedException) {
                    // The broker said no to this message: record it, and the relay halts.
                    failures.add(new Failure(pending.row(), error.getMessage()));
                } else {
                    // The broker or the connection could not take it right now: cancel the batch, try again next tick.
                    return outage(status, sendable.size(), error instanceof ProjectionUnavailableException
                            ? error.getMessage() : error.toString());
                }
            } else if (Boolean.TRUE.equals(confirm.resultNow())) {
                confirmed.add(pending);
            } else {
                failures.add(new Failure(pending.row(), "the broker did not confirm the message"));
            }
        }

        for (Pending pending : confirmed)
            store.markEmitted(pending.row(), at, pending.body());
        if (failures.isEmpty()) {
            log.debug("Outbox relay published {} event(s)", confirmed.size());
            return null;
        }

        for (Failure failure : failures)
            store.recordFailure(failure.row(), failure.error());
        Failure first = failures.getFirst();
        return halt(first.row(), first.error(), confirmed.size() + " of " + sendable.size()
                + " event(s) in this tick were confirmed and marked published; " + failures.size() + " refused");
    }

    private static Halt halt(EventOutboxEntity row, String reason, String batch) {
        return new Halt(row.getEventId(), row.getEventType(), row.getApiKeyId(), row.getVersion(), reason, batch);
    }

    /**
     * Waits until every future has finished, whether it succeeded or failed, but no longer than {@code timeout}.
     * Returns true if all of them finished in time, false if the time ran out. Java's version of
     * {@code Promise.allSettled} with a timeout.
     */
    private static boolean waitForAll(List<CompletableFuture<Boolean>> futures, Duration timeout) {
        CompletableFuture<?>[] futureArray = futures.toArray(new CompletableFuture<?>[0]); // allOf takes an array
        CompletableFuture<Void> allSettled = CompletableFuture.allOf(futureArray);        // done when all are done
        try {
            allSettled.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException oneOfThemFailed) {
            // allOf still waited for every future to finish; one just ended with an error. Each error is looked at
            // by the caller.
            return true;
        } catch (TimeoutException timeIsUp) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Rolls the tick back: nothing is marked published or counted, and the next tick sends the same events again. */
    private Halt outage(TransactionStatus status, int events, String reason) {
        status.setRollbackOnly();
        log.warn("Outbox relay: broker unavailable, cancelled this batch of {} event(s); nothing was marked published "
                + "or counted, retrying in {}. Reason: {}", events, readable(properties.pollInterval()), reason);
        return null;
    }

    /** {@code PT0.2S} as {@code 0.2s}, {@code PT5S} as {@code 5s}, for logs. */
    private static String readable(Duration duration) {
        return duration.toString().substring(2).toLowerCase();
    }

    private record Pending(EventOutboxEntity row, String body) {
    }

    private record Failure(EventOutboxEntity row, String error) {
    }
}
