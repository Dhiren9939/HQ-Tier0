package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Constants;
import com.rabbitmq.stream.Environment;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;
import org.springframework.rabbit.stream.producer.StreamSendException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * {@link ProjectionStream} on a RabbitMQ super stream. The template routes each message by its {@code apiKeyId}
 * application property, which the broker hashes to a partition, so every event of a key lands on the same partition.
 *
 * <p>It also decides what a failed send means. The template fails a send with a {@link StreamSendException} carrying
 * a confirmation code:</p>
 * <ul>
 *   <li>Codes in {@link #OUTAGE_CODES} say the broker or the connection could not take the message right now; sending
 *       it again later works. They become a {@link ProjectionUnavailableException} (an outage).</li>
 *   <li>Any other code is the broker or the client refusing the message for a reason a person must fix (access
 *       refused, no partition to route to, ...). It becomes a {@link ProjectionRefusedException} (a halt).</li>
 *   <li>Any other exception (the connection dropped mid-send, ...) is an outage too.</li>
 * </ul>
 * <p>Messages name the code, for example {@code ACCESS_REFUSED (16)}.</p>
 */
public class RabbitProjectionStream implements ProjectionStream {

    /** Confirmation codes that say nothing about the message itself: try again on the next tick. */
    static final Set<Short> OUTAGE_CODES = Set.of(
            Constants.CODE_MESSAGE_ENQUEUEING_FAILED,          // 10001 client could not queue the message
            Constants.CODE_PRODUCER_NOT_AVAILABLE,             // 10002 producer is reconnecting
            Constants.CODE_PRODUCER_CLOSED,                    // 10003 producer was closed
            Constants.CODE_PUBLISH_CONFIRM_TIMEOUT,            // 10004 the broker did not confirm in time
            Constants.RESPONSE_CODE_STREAM_NOT_AVAILABLE,      // 6     partition leader is moving
            Constants.RESPONSE_CODE_STREAM_DOES_NOT_EXIST,     // 2     stream gone; the next tick declares it again
            Constants.RESPONSE_CODE_PUBLISHER_DOES_NOT_EXIST,  // 18    broker lost the producer, typically right after a reconnect
            Constants.RESPONSE_CODE_INTERNAL_ERROR);           // 15    something failed inside the broker

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
                    if (error == null) {
                        result.complete(Boolean.TRUE.equals(confirmed));
                        return;
                    }
                    Throwable cause = unwrap(error);
                    if (cause instanceof StreamSendException send) {
                        short code = (short) send.getConfirmationCode();
                        if (OUTAGE_CODES.contains(code))
                            result.completeExceptionally(new ProjectionUnavailableException(
                                    "the broker could not take the message right now: " + describe(code), send));
                        else
                            result.completeExceptionally(new ProjectionRefusedException(
                                    "the broker refused the message: " + describe(code), send));
                    } else {
                        result.completeExceptionally(new ProjectionUnavailableException(
                                "the message could not be sent: " + cause, cause));
                    }
                });
        return result;
    }

    /** {@code ACCESS_REFUSED (16)}: the constant's name in the stream client, then the number. */
    static String describe(short code) {
        return Arrays.stream(Constants.class.getFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == short.class)
                .filter(field -> field.getName().startsWith("RESPONSE_CODE_") || field.getName().startsWith("CODE_"))
                .filter(field -> valueOf(field) == code)
                .map(field -> field.getName().replaceFirst("^(RESPONSE_)?CODE_", ""))
                .findFirst()
                .map(name -> name + " (" + code + ")")
                .orElse("code " + code);
    }

    private static short valueOf(Field field) {
        try {
            return field.getShort(null);
        } catch (IllegalAccessException e) {
            return Short.MIN_VALUE;
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
