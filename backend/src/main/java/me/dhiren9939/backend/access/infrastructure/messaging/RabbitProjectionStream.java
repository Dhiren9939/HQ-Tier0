package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageBuilder;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * {@link ProjectionStream} on a RabbitMQ super stream. The template routes each message by its {@code apiKeyId}
 * application property, which the broker hashes to a partition, so every event of a key lands on the same partition.
 *
 * <p>The super stream is declared over the stream protocol on first use rather than at startup, so the application
 * starts even while the broker is down; the relay retries the declaration with its normal backoff.</p>
 */
public class RabbitProjectionStream implements ProjectionStream {
    private final Environment environment;
    private final RabbitStreamTemplate template;
    private final String streamName;
    private final int partitions;
    private volatile boolean declared;

    public RabbitProjectionStream(Environment environment, RabbitStreamTemplate template, String streamName,
                                  int partitions) {
        this.environment = environment;
        this.template = template;
        this.streamName = streamName;
        this.partitions = partitions;
    }

    @Override
    public void declare() {
        if (declared)
            return;
        // Answers "already exists" with success, so declaring an existing super stream is harmless.
        environment.streamCreator().name(streamName).superStream().partitions(partitions).creator().create();
        declared = true;
    }

    @Override
    public MessageBuilder messageBuilder() {
        return template.messageBuilder();
    }

    @Override
    public CompletableFuture<Boolean> send(Message message) {
        return template.send(message);
    }
}
