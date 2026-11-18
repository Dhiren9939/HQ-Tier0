package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageBuilder;

import java.util.concurrent.CompletableFuture;

/** The super stream the relay publishes integration events to. */
public interface ProjectionStream {

    /** Creates the super stream and its partitions if they do not exist yet. Idempotent. */
    void declare();

    MessageBuilder messageBuilder();

    /**
     * Publishes one message. The future completes {@code true} only once the broker confirms the message; it
     * completes {@code false} or exceptionally when the message was not confirmed.
     */
    CompletableFuture<Boolean> send(Message message);
}
