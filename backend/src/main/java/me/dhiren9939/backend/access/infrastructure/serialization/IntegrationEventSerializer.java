package me.dhiren9939.backend.access.infrastructure.serialization;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyAccessLevel;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyScope;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes integration events as the JSON message body, and is the only place that knows that shape.
 *
 * <p>The outbox stores the body ready to publish, so {@link #toJson} produces everything except {@code emittedAt},
 * which only exists once the event is published ({@link #withEmittedAt}).</p>
 *
 * <p>This class is the only gate in front of the outbox: a relay (or CDC later) forwards rows as they are and has no
 * way to skip a bad one, so nothing that breaks the contract may be stored. {@link #toJson} therefore checks the
 * finished JSON, not the event object, against the whole wire contract and refuses anything else. A refusal throws
 * {@link IntegrationEventSerializationException} naming the field, which rolls the key change back.</p>
 *
 * <p>The contract: exactly the keys {@link #STORED_KEYS}, no others; nothing is null except {@code expiresAt} (null
 * means the key never expires); ids are canonical UUIDs; instants are canonical ISO-8601 UTC; {@code version} is at
 * least 1; text is not blank and at most {@value #MAX_TEXT} characters; {@code permissions} is an object of known
 * scope to known level, where a scope that is absent means no access and {@code INGESTION} only allows
 * {@code WRITE}. Key order is not part of the contract.</p>
 */
@Component
@RequiredArgsConstructor
public class IntegrationEventSerializer {
    static final String EVENT_ID = "eventId";
    static final String EVENT_TYPE = "eventType";
    static final String EXPIRES_AT = "expiresAt";
    static final String EMITTED_AT = "emittedAt";
    static final int MAX_TEXT = 256;

    /** Every key a stored body carries, nullable or not. */
    static final Set<String> STORED_KEYS = Set.of(EVENT_ID, EVENT_TYPE, "apiKeyName", "apiKeyId", "version",
            "occurredAt", "userId", "accessId", "accessSecretHash", "permissions", "createdAt", EXPIRES_AT);

    private static final Set<String> EVENT_TYPES = Set.of(ApiKeyCreatedIntegrationEvent.EVENT_TYPE,
            ApiKeyUpdatedIntegrationEvent.EVENT_TYPE, ApiKeyDeletedIntegrationEvent.EVENT_TYPE);
    private static final Set<String> SCOPES = names(ApiKeyScope.values());
    private static final Set<String> LEVELS = names(ApiKeyAccessLevel.values());

    private final JsonMapper json;

    /** The message body for a new outbox row: everything the event carries plus its id, without {@code emittedAt}. */
    public String toJson(UUID eventId, ApiKeyIntegrationEvent event) {
        if (eventId == null)
            throw new IntegrationEventSerializationException("An integration event needs an eventId");
        if (event == null)
            throw new IntegrationEventSerializationException("There is no integration event to write");
        if (event.emittedAt() != null)
            throw new IntegrationEventSerializationException(
                    "Integration event " + event.eventType() + " already has " + EMITTED_AT
                            + "; it is only added when the event is published");
        try {
            ObjectNode body = json.createObjectNode();
            body.put(EVENT_ID, eventId.toString());
            body.put(EVENT_TYPE, event.eventType());
            ObjectNode fields = json.valueToTree(event);
            fields.remove(EMITTED_AT);
            body.setAll(fields);
            validate(body, eventId.toString());
            return json.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IntegrationEventSerializationException("Cannot write " + event.eventType() + " as JSON", e);
        }
    }

    /** The stored body with the time it was published. A stored body that breaks the contract is not published. */
    public String withEmittedAt(String payload, Instant emittedAt) {
        if (emittedAt == null)
            throw new IntegrationEventSerializationException("A published event needs " + EMITTED_AT);
        try {
            if (!(json.readTree(payload) instanceof ObjectNode body))
                throw new IntegrationEventSerializationException("The outbox payload is not a JSON object");
            validate(body, null);
            body.put(EMITTED_AT, emittedAt.toString());
            return json.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IntegrationEventSerializationException("The outbox payload is not valid JSON", e);
        }
    }

    /** Throws unless the body is exactly a stored integration event; {@code expectedEventId} is checked when known. */
    private static void validate(ObjectNode body, String expectedEventId) {
        for (Map.Entry<String, JsonNode> field : body.properties())
            if (!STORED_KEYS.contains(field.getKey()))
                throw invalid(field.getKey(), "is not part of the event contract");
        for (String key : STORED_KEYS)
            if (!body.has(key))
                throw invalid(key, "is missing");

        String eventId = uuid(body, EVENT_ID);
        if (expectedEventId != null && !expectedEventId.equals(eventId))
            throw invalid(EVENT_ID, "is " + eventId + " but the event was given " + expectedEventId);
        String eventType = text(body, EVENT_TYPE);
        if (!EVENT_TYPES.contains(eventType))
            throw invalid(EVENT_TYPE, "is " + eventType + ", expected one of " + EVENT_TYPES);

        text(body, "apiKeyName");
        uuid(body, "apiKeyId");
        uuid(body, "userId");
        text(body, "accessId");
        text(body, "accessSecretHash");
        version(body);
        instant(body, "occurredAt");
        instant(body, "createdAt");
        if (!body.get(EXPIRES_AT).isNull())
            instant(body, EXPIRES_AT);
        permissions(body.get("permissions"));
    }

    private static String text(ObjectNode body, String key) {
        JsonNode value = body.get(key);
        if (value.isNull())
            throw invalid(key, "is null; only " + EXPIRES_AT + " may be null");
        if (!value.isString())
            throw invalid(key, "is not text");
        String text = value.asString();
        if (text.isBlank())
            throw invalid(key, "is blank");
        if (text.length() > MAX_TEXT)
            throw invalid(key, "is longer than " + MAX_TEXT + " characters");
        return text;
    }

    private static String uuid(ObjectNode body, String key) {
        String text = text(body, key);
        try {
            if (UUID.fromString(text).toString().equals(text))
                return text;
        } catch (IllegalArgumentException ignored) {
            // reported below
        }
        throw invalid(key, "is not a canonical lower-case UUID: " + text);
    }

    private static void instant(ObjectNode body, String key) {
        String text = text(body, key);
        try {
            if (Instant.parse(text).toString().equals(text))
                return;
        } catch (DateTimeParseException ignored) {
            // reported below
        }
        throw invalid(key, "is not an ISO-8601 UTC instant (as Instant.toString writes it): " + text);
    }

    private static void version(ObjectNode body) {
        JsonNode value = body.get("version");
        if (value.isNull())
            throw invalid("version", "is null; only " + EXPIRES_AT + " may be null");
        if (!value.isIntegralNumber() || !value.canConvertToLong())
            throw invalid("version", "is not a whole number");
        if (value.asLong() < 1)
            throw invalid("version", "is " + value.asLong() + ", expected at least 1");
    }

    private static void permissions(JsonNode permissions) {
        if (permissions.isNull())
            throw invalid("permissions", "is null; no access is {}");
        if (!permissions.isObject())
            throw invalid("permissions", "is not an object of scope to level");
        for (Map.Entry<String, JsonNode> grant : permissions.properties()) {
            String scope = grant.getKey();
            JsonNode level = grant.getValue();
            if (!SCOPES.contains(scope))
                throw invalid("permissions", "has unknown scope " + scope + ", expected one of " + SCOPES);
            if (!level.isString() || !LEVELS.contains(level.asString()))
                throw invalid("permissions", "has " + scope + " set to " + level + ", expected one of " + LEVELS
                        + "; no access is a missing scope");
            if (scope.equals(ApiKeyScope.INGESTION.name()) && !level.asString().equals(ApiKeyAccessLevel.WRITE.name()))
                throw invalid("permissions", "has INGESTION set to " + level.asString() + "; INGESTION only allows WRITE");
        }
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private static IntegrationEventSerializationException invalid(String field, String problem) {
        return new IntegrationEventSerializationException("Integration event field " + field + " " + problem);
    }
}
