package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageBuilder;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Turns an outbox row into the stream message consumers read.
 *
 * <p>The body is a JSON object whose fields are {@code eventType} followed by exactly the components of the
 * integration event records ({@code ApiKeyCreatedIntegrationEvent} and its siblings), in the same order. The row
 * already holds the event as the {@code ApiKeyIntegrationEventMapper} encoded it, so nothing is re-encoded here;
 * {@code permissions} is embedded as the JSON object the outbox stores and {@code emittedAt} is the publish time.</p>
 *
 * <p>Message properties: {@code messageId} is the event id and the content type is JSON. Application properties
 * {@code eventType}, {@code apiKeyId} (the partition routing key) and {@code version} let consumers filter without
 * parsing the body.</p>
 */
public class IntegrationEventMessages {
    public static final String CONTENT_TYPE = "application/json";
    public static final String EVENT_TYPE = "eventType";
    public static final String API_KEY_ID = "apiKeyId";
    public static final String VERSION = "version";

    private final JsonMapper json;

    public IntegrationEventMessages(JsonMapper json) {
        this.json = json;
    }

    public Message toMessage(EventOutboxEntity row, Instant emittedAt, MessageBuilder builder) {
        byte[] body = body(row, emittedAt);
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
                .addData(body)
                .build();
    }

    byte[] body(EventOutboxEntity row, Instant emittedAt) {
        ObjectNode node = json.createObjectNode();
        node.put("eventType", row.getEventType());
        node.put("apiKeyName", row.getApiKeyName());
        node.put("apiKeyId", row.getApiKeyId().toString());
        node.put("version", row.getVersion());
        node.put("occurredAt", text(row.getOccurredAt()));
        node.put("emittedAt", text(emittedAt));
        node.put("userId", row.getUserId().toString());
        node.put("accessId", row.getAccessId());
        node.put("accessSecretHash", row.getAccessSecretHash());
        node.set("permissions", permissions(row));
        node.put("createdAt", text(row.getCreatedAt()));
        node.put("expiresAt", text(row.getExpiresAt()));
        try {
            return json.writeValueAsBytes(node);
        } catch (JacksonException e) {
            throw new UnpublishableEventException("Cannot serialise outbox row " + row.getEventId(), e);
        }
    }

    private JsonNode permissions(EventOutboxEntity row) {
        JsonNode permissions;
        try {
            permissions = json.readTree(row.getPermissions());
        } catch (JacksonException e) {
            throw new UnpublishableEventException("Permissions of outbox row " + row.getEventId() + " are not JSON", e);
        }
        if (permissions == null || !permissions.isObject())
            throw new UnpublishableEventException("Permissions of outbox row " + row.getEventId() + " are not a JSON object");
        return permissions;
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
