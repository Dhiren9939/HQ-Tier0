package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Constants;
import com.rabbitmq.stream.Environment;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;
import org.springframework.rabbit.stream.producer.StreamSendException;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * {@link ProjectionStream} on a RabbitMQ super stream. The template routes each message by its {@code apiKeyId}
 * application property, which the broker hashes to a partition, so every event of a key lands on the same partition.
 *
 * <p>It also decides what a failed send means. The template fails a send with a {@link StreamSendException} carrying
 * the confirmation code. Codes that are about the client or the connection (the producer is closed or unavailable,
 * the confirm timed out, the stream is not reachable) are outages and stay exceptional. Any other code is the broker
 * answering about the message, so it becomes a {@link ProjectionRefusedException}.</p>
 */
public class RabbitProjectionStream implements ProjectionStream {

    /** Confirmation codes that say nothing about the message itself. */
    private static final Set<Integer> OUTAGE_CODES = Set.of(
            (int) Constants.CODE_MESSAGE_ENQUEUEING_FAILED,
            (int) Constants.CODE_PRODUCER_NOT_AVAILABLE,
            (int) Constants.CODE_PRODUCER_CLOSED,
            (int) Constants.CODE_PUBLISH_CONFIRM_TIMEOUT,
            (int) Constants.RESPONSE_CODE_STREAM_NOT_AVAILABLE,
            (int) Constants.RESPONSE_CODE_STREAM_DOES_NOT_EXIST);

    private final Environment environment;
    private final RabbitStreamTemplate template;
    private final String streamName;
    private final int partitions;

    public RabbitProjectionStream(Environment environment, RabbitStreamTemplate template, String streamName,
                                  int partitions) {
        this.environment = environment;
        this.template = template;
        this.streamName = streamName;
        this.partitions = partitions;
    }

    /** Declared on every tick, not once: a broker that lost its data gets the super stream back. */
    @Override
    public void declare() {
        // The broker answers "already exists" with success, so declaring an existing super stream is harmless.
        environment.streamCreator().name(streamName).superStream().partitions(partitions).creator().create();
    }

    @Override
    public CompletableFuture<Boolean> send(EventOutboxEntity row, String body) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        template.send(ProjectionMessages.toMessage(row, body, template.messageBuilder()))
                .whenComplete((confirmed, error) -> {
                    if (error == null)
                        result.complete(Boolean.TRUE.equals(confirmed));
                    else if (unwrap(error) instanceof StreamSendException send
                            && !OUTAGE_CODES.contains(send.getConfirmationCode()))
                        result.completeExceptionally(new ProjectionRefusedException(
                                "Broker refused the message (code " + send.getConfirmationCode() + "): "
                                        + send.getMessage(), send));
                    else
                        result.completeExceptionally(unwrap(error));
                });
        return result;
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
