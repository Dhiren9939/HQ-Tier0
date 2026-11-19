package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Constants;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.Message;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;
import org.springframework.rabbit.stream.producer.StreamSendException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * {@link ProjectionStream} on a RabbitMQ super stream.
 *
 * <p>Each message is routed by its {@code apiKeyId} application property (see {@code OutboxRelayConfig}), which the
 * broker hashes to a partition, so every event of a key lands on the same partition.</p>
 *
 * <p>When a send fails, this class decides what the failure means for the relay:</p>
 * <ul>
 *   <li><b>Outage</b> ({@link ProjectionUnavailableException}): the broker or the connection could not take the
 *       message right now. Sending it again later works. These are the {@link #OUTAGE_CODES} and any failure that
 *       carries no code at all.</li>
 *   <li><b>Refusal</b> ({@link ProjectionRefusedException}): any other code. The broker or the client will not take
 *       the message until a person fixes something (access refused, no partition to route to, ...).</li>
 * </ul>
 */
public class RabbitProjectionStream implements ProjectionStream {

    /** Confirmation codes that say nothing about the message itself: try again on the next tick. */
    static final Set<Short> OUTAGE_CODES = Set.of(
            Constants.CODE_MESSAGE_ENQUEUEING_FAILED,          // 10001 the client could not queue the message
            Constants.CODE_PRODUCER_NOT_AVAILABLE,             // 10002 the producer is reconnecting
            Constants.CODE_PRODUCER_CLOSED,                    // 10003 the producer was closed
            Constants.CODE_PUBLISH_CONFIRM_TIMEOUT,            // 10004 the broker did not confirm in time
            Constants.RESPONSE_CODE_STREAM_DOES_NOT_EXIST,     // 2     the stream is gone; the next tick declares it again
            Constants.RESPONSE_CODE_STREAM_NOT_AVAILABLE,      // 6     the partition leader is moving
            Constants.RESPONSE_CODE_INTERNAL_ERROR,            // 15    something failed inside the broker
            Constants.RESPONSE_CODE_PUBLISHER_DOES_NOT_EXIST); // 18    the broker lost the producer, right after a reconnect

    /** Code number to name, e.g. 16 to {@code ACCESS_REFUSED}, read once from the stream client's constants. */
    private static final Map<Short, String> CODE_NAMES = codeNames();

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

    /** Called on every tick, not once, so a broker that lost its data gets the super stream back. */
    @Override
    public void declare() {
        // The broker answers "already exists" with success, so declaring an existing super stream is harmless.
        environment.streamCreator().name(streamName).superStream().partitions(partitions).creator().create();
    }

    @Override
    public CompletableFuture<Boolean> send(EventOutboxEntity row, String body) {
        Message message = ProjectionMessages.toMessage(row, body, template.messageBuilder());

        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        template.send(message).whenComplete((confirmed, error) -> {
            if (error == null)
                answer.complete(Boolean.TRUE.equals(confirmed));
            else
                answer.completeExceptionally(classify(error));
        });
        return answer;
    }

    /** Turns a failed send into an outage (try again next tick) or a refusal (halt). */
    static RuntimeException classify(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;

        if (!(cause instanceof StreamSendException failedSend))
            return new ProjectionUnavailableException("the message could not be sent: " + cause, cause);

        short code = (short) failedSend.getConfirmationCode();
        if (OUTAGE_CODES.contains(code))
            return new ProjectionUnavailableException(
                    "the broker could not take the message right now: " + describe(code), failedSend);
        return new ProjectionRefusedException("the broker refused the message: " + describe(code), failedSend);
    }

    /** {@code ACCESS_REFUSED (16)}, or {@code code 999} for a code the stream client does not name. */
    static String describe(short code) {
        String name = CODE_NAMES.get(code);
        return name == null ? "code " + code : name + " (" + code + ")";
    }

    /** Reads every {@code CODE_*} and {@code RESPONSE_CODE_*} constant of the stream client, without the prefix. */
    private static Map<Short, String> codeNames() {
        Map<Short, String> names = new HashMap<>();
        for (Field field : Constants.class.getFields()) {
            boolean isCode = field.getName().startsWith("CODE_") || field.getName().startsWith("RESPONSE_CODE_");
            if (!isCode || field.getType() != short.class || !Modifier.isStatic(field.getModifiers()))
                continue;
            try {
                names.putIfAbsent(field.getShort(null), field.getName().replaceFirst("^(RESPONSE_)?CODE_", ""));
            } catch (IllegalAccessException ignored) {
                // public constants; cannot happen
            }
        }
        return Map.copyOf(names);
    }
}
