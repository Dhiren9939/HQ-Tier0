package me.dhiren9939.backend.access.infrastructure.messaging;

import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;

import java.util.concurrent.CompletableFuture;

/**
 * The super stream the relay publishes integration events to. This is all the relay knows about the broker, so the
 * relay can be tested with a scripted fake.
 */
public interface ProjectionStream {

    /** Creates the super stream and its partitions if they do not exist yet. Idempotent; throws if the broker is down. */
    void declare();

    /**
     * Publishes one outbox row with the given body (the stored payload plus {@code emittedAt}). The future:
     * <ul>
     *   <li>completes {@code true} once the broker confirmed the message;</li>
     *   <li>completes {@code false}, or exceptionally with {@link ProjectionRefusedException}, when the broker
     *       answered that it will not take the message (a refusal: the relay halts);</li>
     *   <li>completes exceptionally with anything else when there was no answer about the message, for example the
     *       connection is down (an outage: the relay cancels the batch and repolls).</li>
     * </ul>
     */
    CompletableFuture<Boolean> send(EventOutboxEntity row, String body);
}
