package me.dhiren9939.backend.access.infrastructure.serialization;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes integration events as the JSON message body, and is the only place that knows that shape.
 *
 * <p>The outbox stores the body ready to publish, so {@link #toJson} produces everything except {@code emittedAt},
 * which only exists once the event is published ({@link #withEmittedAt}). The wire contract has no nulls except
 * {@code expiresAt} (null means the key never expires); any other null means the mapper produced an incomplete event
 * and is rejected here, before it reaches the outbox. Permissions are an object of scope to level, and a scope
 * that is absent means no access. Key order is not part of the contract.</p>
 */
@Component
@RequiredArgsConstructor
public class IntegrationEventSerializer {
    static final String EXPIRES_AT = "expiresAt";
    static final String EMITTED_AT = "emittedAt";

    private final JsonMapper json;

    /** The message body for a new outbox row: everything the event carries plus its id, without {@code emittedAt}. */
    public String toJson(UUID eventId, ApiKeyIntegrationEvent event) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("eventId", eventId.toString());
            body.put("eventType", event.eventType());
            ObjectNode fields = json.valueToTree(event);
            fields.remove(EMITTED_AT);
            body.setAll(fields);
            for (Map.Entry<String, JsonNode> field : body.properties())
                if (field.getValue().isNull() && !EXPIRES_AT.equals(field.getKey()))
                    throw new IntegrationEventSerializationException(
                            "Integration event " + event.eventType() + " has no " + field.getKey()
                                    + "; only " + EXPIRES_AT + " may be null");
            return json.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IntegrationEventSerializationException("Cannot write " + event.eventType() + " as JSON", e);
        }
    }

    /** The stored body with the time it was published. */
    public String withEmittedAt(String payload, Instant emittedAt) {
        try {
            if (!(json.readTree(payload) instanceof ObjectNode body))
                throw new IntegrationEventSerializationException("The outbox payload is not a JSON object");
            body.put(EMITTED_AT, emittedAt.toString());
            return json.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IntegrationEventSerializationException("The outbox payload is not valid JSON", e);
        }
    }
}
