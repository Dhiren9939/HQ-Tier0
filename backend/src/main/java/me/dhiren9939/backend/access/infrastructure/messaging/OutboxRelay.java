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
 * Publishes the outbox to the projection super stream, at least once, and halts on the first event it cannot publish.
 *
 * <p>Every tick runs in one transaction and goes through three steps:</p>
 * <ol>
 *   <li><b>Build</b> a message for every pending row (up to {@code batch-size}, oldest first). If one stored payload
 *       breaks the event contract, the whole batch is dropped, nothing is sent, and the relay halts.</li>
 *   <li><b>Send</b> them all and wait once for every answer, at most {@code confirm-timeout} in total. If the broker
 *       or the connection is unavailable, the batch is cancelled (rolled back) and the next tick sends it again.</li>
 *   <li><b>Stamp</b> every confirmed row. If the broker refused a message, its row records why and the relay halts.</li>
 * </ol>
 *
 * <p>Nothing is retried: a halt means a contract or configuration problem that a person must fix. Restarting the
 * application clears the halt, and publishing starts again from the stuck event, which is still first in order. A
 * cancelled batch may already have been partly confirmed, so consumers can see an event twice; every event is a full
 * snapshot with the key's {@code version}, so they keep the highest one.</p>
 */
@Slf4j
public class OutboxRelay {
    private final OutboxStore store;
    private final ProjectionStream stream;
    private final IntegrationEventSerializer serializer;
    private final OutboxRelayProperties properties;
    private final Clock clock;
    private final TransactionOperations transactions;

    /** Set when the relay stops; it stays set until the application restarts. */
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
     * Why the relay stopped.
     *
     * @param reason why this event could not be published
     * @param batch  what happened to the other events taken in the same tick
     */
    public record Halt(UUID eventId, String eventType, UUID apiKeyId, long version, String reason, String batch) {

        static Halt at(EventOutboxEntity row, String reason, String batch) {
            return new Halt(row.getEventId(), row.getEventType(), row.getApiKeyId(), row.getVersion(), reason, batch);
        }
    }

    /** Present once the relay has halted. */
    public Optional<Halt> halt() {
        return Optional.ofNullable(halt);
    }

    @Scheduled(fixedDelayString = "${access.outbox.relay.poll-interval:PT2S}")
    public void tick() {
        if (halt != null)
            return;

        Halt stuck = transactions.execute(this::publishOneBatch);
        if (stuck != null) {
            halt = stuck;
            logHalt(stuck);
        }
    }

    /** One batch, in the tick's transaction. Returns the halt when an event could not be published, otherwise null. */
    private Halt publishOneBatch(TransactionStatus transaction) {
        if (!declareStream())
            return null;

        List<EventOutboxEntity> rows = store.lockPending(properties.batchSize());
        if (rows.isEmpty())
            return null;

        // PostgreSQL keeps microseconds; truncating keeps the emitted_at column and the JSON emittedAt identical.
        Instant emittedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        // 1. Build every message before sending anything: one broken payload drops the whole batch.
        List<Outgoing> batch = new ArrayList<>();
        for (EventOutboxEntity row : rows) {
            try {
                batch.add(new Outgoing(row, serializer.withEmittedAt(row.getPayload(), emittedAt)));
            } catch (IntegrationEventSerializationException e) {
                return haltOnBrokenPayload(row, e.getMessage(), rows.size());
            }
        }

        // 2. Send everything and wait for every answer.
        List<Answer> answers;
        try {
            answers = sendAndAwaitAnswers(batch);
        } catch (BrokerUnavailable e) {
            return cancelBatch(transaction, batch.size(), e.getMessage());
        }

        // 3. Stamp what was confirmed. Any refusal halts the relay.
        List<Answer> confirmed = answers.stream().filter(Answer::confirmed).toList();
        List<Answer> refused = answers.stream().filter(answer -> !answer.confirmed()).toList();

        confirmed.forEach(answer -> store.markEmitted(answer.row(), emittedAt, answer.body()));
        if (refused.isEmpty()) {
            log.debug("Outbox relay published {} event(s)", confirmed.size());
            return null;
        }
        return haltOnRefusals(refused, confirmed.size(), batch.size());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Talking to the broker
    // ---------------------------------------------------------------------------------------------------------------

    /** Makes sure the super stream exists. Returns false, and logs why, when the broker cannot be reached. */
    private boolean declareStream() {
        try {
            stream.declare();
            return true;
        } catch (RuntimeException e) {
            log.warn("Outbox relay: cannot reach the broker to declare stream {}; no events were taken, retrying in {}. "
                    + "Reason: {}", properties.streamName(), readable(properties.pollInterval()), e.toString());
            return false;
        }
    }

    /** Sends the whole batch, then waits once for all the confirms. Throws if the broker is unavailable. */
    private List<Answer> sendAndAwaitAnswers(List<Outgoing> batch) throws BrokerUnavailable {
        List<CompletableFuture<Boolean>> confirms = new ArrayList<>();
        try {
            for (Outgoing message : batch)
                confirms.add(stream.send(message.row(), message.body()));
        } catch (RuntimeException e) {
            throw new BrokerUnavailable("sending failed: " + e);
        }

        if (!waitForAll(confirms, properties.confirmTimeout()))
            throw new BrokerUnavailable(
                    "the broker did not confirm every message within " + readable(properties.confirmTimeout()));

        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++)
            answers.add(answerFor(batch.get(i), confirms.get(i)));
        return answers;
    }

    /**
     * What the broker said about one message: confirmed, or refused with a reason. A failure that is not about the
     * message (the broker or the connection was unavailable) is thrown instead, because it cancels the whole batch.
     */
    private static Answer answerFor(Outgoing message, CompletableFuture<Boolean> confirm) throws BrokerUnavailable {
        if (!confirm.isCompletedExceptionally()) {
            boolean confirmed = Boolean.TRUE.equals(confirm.resultNow());
            return confirmed ? Answer.confirmed(message) : Answer.refused(message, "the broker did not confirm the message");
        }

        Throwable error = confirm.exceptionNow();
        if (error instanceof ProjectionRefusedException)
            return Answer.refused(message, error.getMessage());
        if (error instanceof ProjectionUnavailableException)
            throw new BrokerUnavailable(error.getMessage());
        throw new BrokerUnavailable(error.toString());
    }

    /**
     * Waits until every future has finished, whether it succeeded or failed, but no longer than {@code timeout}.
     * Returns true if all of them finished in time, false if the time ran out. Java's {@code Promise.allSettled}.
     */
    private static boolean waitForAll(List<CompletableFuture<Boolean>> futures, Duration timeout) {
        CompletableFuture<?>[] futureArray = futures.toArray(new CompletableFuture<?>[0]); // allOf takes an array
        CompletableFuture<Void> allSettled = CompletableFuture.allOf(futureArray);        // done when all are done
        try {
            allSettled.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException oneOfThemFailed) {
            return true; // still all finished; each failure is read in answerFor
        } catch (TimeoutException timeIsUp) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Outcomes
    // ---------------------------------------------------------------------------------------------------------------

    /** Broker unavailable: rolls the tick back, so nothing is marked or counted and the next tick sends it all again. */
    private Halt cancelBatch(TransactionStatus transaction, int events, String reason) {
        transaction.setRollbackOnly();
        log.warn("Outbox relay: broker unavailable, cancelled this batch of {} event(s); nothing was marked published "
                + "or counted, retrying in {}. Reason: {}", events, readable(properties.pollInterval()), reason);
        return null;
    }

    /** A stored payload breaks the contract: nothing was sent; only that row records the error. */
    private Halt haltOnBrokenPayload(EventOutboxEntity row, String problem, int batchSize) {
        store.recordFailure(row, "Unpublishable: " + problem);
        return Halt.at(row, "its stored payload breaks the event contract: " + problem,
                "dropped: none of the " + batchSize + " event(s) taken in this tick were sent");
    }

    /** The broker refused messages: each refused row records why; the relay halts at the first one. */
    private Halt haltOnRefusals(List<Answer> refused, int confirmed, int batchSize) {
        refused.forEach(answer -> store.recordFailure(answer.row(), answer.refusal()));
        Answer first = refused.getFirst();
        return Halt.at(first.row(), first.refusal(), confirmed + " of " + batchSize
                + " event(s) in this tick were confirmed and marked published; " + refused.size() + " refused");
    }

    private static void logHalt(Halt halt) {
        log.error("""
                Outbox relay HALTED: nothing more will be published until the application is restarted.
                  event:   {} ({}) for apiKeyId {} version {}
                  reason:  {}
                  batch:   {}
                  recover: fix the cause, then restart the application; publishing resumes from this event. \
                The error is also stored in access.outbox.last_error for event_id {}.""",
                halt.eventId(), halt.eventType(), halt.apiKeyId(), halt.version(), halt.reason(), halt.batch(),
                halt.eventId());
    }

    /** {@code PT0.2S} as {@code 0.2s}, {@code PT5S} as {@code 5s}, for logs. */
    private static String readable(Duration duration) {
        return duration.toString().substring(2).toLowerCase();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Small values used during a tick
    // ---------------------------------------------------------------------------------------------------------------

    /** A row and the exact body that will be sent for it. */
    private record Outgoing(EventOutboxEntity row, String body) {
    }

    /** The broker's answer about one message: confirmed, or refused with the reason. */
    private record Answer(Outgoing message, boolean confirmed, String refusal) {

        static Answer confirmed(Outgoing message) {
            return new Answer(message, true, null);
        }

        static Answer refused(Outgoing message, String refusal) {
            return new Answer(message, false, refusal);
        }

        EventOutboxEntity row() {
            return message.row();
        }

        String body() {
            return message.body();
        }
    }

    /** The broker or the connection could not take the batch right now. Cancels the batch; never halts. */
    private static final class BrokerUnavailable extends Exception {
        BrokerUnavailable(String reason) {
            super(reason, null, false, false);
        }
    }
}
