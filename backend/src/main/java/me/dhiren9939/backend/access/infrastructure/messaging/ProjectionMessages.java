package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageBuilder;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;

import java.nio.charset.StandardCharsets;

/**
 * Turns an outbox row and its body into the stream message consumers read.
 *
 * <p>The body is used exactly as given: it is the stored payload with {@code emittedAt} added by the serializer, and
 * nothing here re-encodes it. Message properties: {@code messageId} is the event id and the content type is JSON.
 * Application properties {@code eventType}, {@code apiKeyId} (the partition routing key) and {@code version} let
 * consumers filter without parsing the body.</p>
 */
public final class ProjectionMessages {
    public static final String CONTENT_TYPE = "application/json";
    public static final String EVENT_TYPE = "eventType";
    public static final String API_KEY_ID = "apiKeyId";
    public static final String VERSION = "version";

    private ProjectionMessages() {
    }

    public static Message toMessage(EventOutboxEntity row, String body, MessageBuilder builder) {
        return builder
                .properties()
                    .messageId(row.getEventId())
                    .contentType(CONTENT_TYPE)
                .messageBuilder()
                .applicationProperties()
                    .entry(EVENT_TYPE, row.getEventType())
                    .entry(API_KEY_ID, row.getApiKeyId().toString())
                    .entry(VERSION, row.getVersion())
                .messageBuilder()
                .addData(body.getBytes(StandardCharsets.UTF_8))
                .build();
    }
}
