package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Constants;
import com.rabbitmq.stream.Message;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import me.dhiren9939.backend.access.infrastructure.persistence.repositories.OutboxStore;
import org.springframework.rabbit.stream.producer.StreamSendException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes unpublished outbox rows to the projection super stream, at least once.
 *
 * <p><b>Publisher confirms.</b> A row is stamped {@code emitted_at} only after the broker confirmed its message.
 * Each cycle sends the batch, then waits on every message's confirm, bounded by {@code confirm-timeout}.</p>
 *
 * <ul>
 *   <li><b>All confirmed</b>: the rows are stamped and the backoff resets.</li>
 *   <li><b>Transient failure</b> (the broker or connection is unavailable, the producer is closed, a confirm times out
 *       or the client could not enqueue): the whole batch is dropped. Nothing is stamped, the cycle backs off
 *       exponentially and the next cycle sends the same rows again, in the same order. Messages already confirmed are
 *       then published twice, which is harmless: every event is a full snapshot carrying the key's {@code version},
 *       and consumers keep the highest version per key. Transient failures never count against a row.</li>
 *   <li><b>Refused</b> (the broker answered the publish with an error for that message, or there was no route):
 *       the row's {@code attempts} grows; at {@code max-attempts} it is moved to the dead-letter table. The other rows
 *       of the batch are stamped as usual.</li>
 *   <li><b>Unpublishable</b> (the row cannot be turned into a message): dead-lettered at once, retrying cannot help.</li>
 * </ul>
 *
 * <p>Rows are taken oldest first with {@code FOR UPDATE SKIP LOCKED} and stay locked for the cycle's transaction.
 * Each key routes to one partition, so its events arrive in {@code occurred_at} order.</p>
 */
@Slf4j
public class OutboxRelay {

    /** Client-side confirm failures that say nothing about the message itself. */
    private static final Set<Integer> TRANSIENT_CODES = Set.of(
            (int) Constants.CODE_MESSAGE_ENQUEUEING_FAILED,
            (int) Constants.CODE_PRODUCER_NOT_AVAILABLE,
            (int) Constants.CODE_PRODUCER_CLOSED,
            (int) Constants.CODE_PUBLISH_CONFIRM_TIMEOUT,
            (int) Constants.RESPONSE_CODE_STREAM_NOT_AVAILABLE,
            (int) Constants.RESPONSE_CODE_STREAM_DOES_NOT_EXIST);

    enum Outcome { IDLE, PUBLISHED, DROPPED }

    private final OutboxStore store;
    private final ProjectionStream stream;
    private final IntegrationEventMessages messages;
    private final OutboxRelayProperties properties;
    private final Clock clock;
    private final TransactionOperations transactions;

    private Instant nextAttemptAt = Instant.MIN;
    private Duration backoff;

    public OutboxRelay(OutboxStore store, ProjectionStream stream, IntegrationEventMessages messages,
                       OutboxRelayProperties properties, Clock clock, TransactionOperations transactions) {
        this.store = store;
        this.stream = stream;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
        this.transactions = transactions;
    }

    /** One cycle, unless the relay is backing off after a transient failure. */
    @Scheduled(fixedDelayString = "${access.outbox.relay.poll-interval:PT1S}")
    public void poll() {
        if (clock.instant().isBefore(nextAttemptAt))
            return;
        Outcome outcome = transactions.execute(status -> relayBatch());
        if (outcome == Outcome.DROPPED)
            backOff();
        else
            backoff = null;
    }

    Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    private void backOff() {
        backoff = backoff == null ? properties.backoffBase() : min(backoff.multipliedBy(2), properties.backoffMax());
        nextAttemptAt = clock.instant().plus(backoff);
        log.warn("Outbox relay dropped its batch; retrying in {}", backoff);
    }

    private Outcome relayBatch() {
        List<EventOutboxEntity> rows = store.lockPending(properties.batchSize());
        if (rows.isEmpty())
            return Outcome.IDLE;
        Instant now = clock.instant();

        List<EventOutboxEntity> sent = new ArrayList<>();
        List<CompletableFuture<Boolean>> confirms = new ArrayList<>();
        try {
            stream.declare();
            for (EventOutboxEntity row : rows) {
                Message message;
                try {
                    message = messages.toMessage(row, now, stream.messageBuilder());
                } catch (UnpublishableEventException e) {
                    log.error("Dead-lettering outbox row {}: {}", row.getEventId(), e.getMessage());
                    store.deadLetter(row, e.getMessage(), now);
                    continue;
                }
                sent.add(row);
                confirms.add(stream.send(message));
            }
        } catch (RuntimeException e) {
            log.warn("Outbox relay could not publish: {}", e.toString());
            return Outcome.DROPPED;
        }

        List<EventOutboxEntity> confirmed = new ArrayList<>();
        List<Refusal> refused = new ArrayList<>();
        long deadline = System.nanoTime() + properties.confirmTimeout().toNanos();
        for (int i = 0; i < sent.size(); i++) {
            EventOutboxEntity row = sent.get(i);
            try {
                Boolean ok = confirms.get(i).get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (Boolean.TRUE.equals(ok))
                    confirmed.add(row);
                else
                    refused.add(new Refusal(row, "Broker did not confirm the message"));
            } catch (TimeoutException e) {
                log.warn("Outbox relay timed out waiting for publish confirms");
                return Outcome.DROPPED;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Outcome.DROPPED;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (isTransient(cause)) {
                    log.warn("Outbox relay publish was not confirmed: {}", String.valueOf(cause));
                    return Outcome.DROPPED;
                }
                refused.add(new Refusal(row, String.valueOf(cause)));
            }
        }

        store.markEmitted(confirmed, now);
        for (Refusal refusal : refused) {
            int attempts = store.recordFailure(refusal.row(), refusal.error());
            if (attempts >= properties.maxAttempts()) {
                log.error("Dead-lettering outbox row {} after {} refused publishes: {}",
                        refusal.row().getEventId(), attempts, refusal.error());
                store.deadLetter(refusal.row(), refusal.error(), now);
            } else {
                log.warn("Outbox row {} was refused ({} of {}): {}",
                        refusal.row().getEventId(), attempts, properties.maxAttempts(), refusal.error());
            }
        }
        return Outcome.PUBLISHED;
    }

    /** Only an explicit broker answer about the message counts against the row; everything else is the connection. */
    private static boolean isTransient(Throwable cause) {
        if (cause instanceof StreamSendException send)
            return TRANSIENT_CODES.contains(send.getConfirmationCode());
        return true;
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private record Refusal(EventOutboxEntity row, String error) {
    }
}
