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

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Writes integration events as the JSON message body, and is the only place that knows that shape.
 *
 * <p>The outbox stores the body ready to publish, so {@link #toJson} produces everything except {@code emittedAt},
 * which only exists once the event is published ({@link #withEmittedAt}).</p>
 *
 * <p>This class is the only gate in front of the outbox: a relay (or CDC later) forwards rows as they are and has no
 * way to skip a bad one, so nothing that breaks the contract may be stored. Every body, written or about to be
 * published, is checked in three steps, and a refusal throws {@link IntegrationEventSerializationException} naming
 * the field:</p>
 * <ol>
 *   <li><b>Keys</b>: exactly {@link #STORED_KEYS}, which is derived from the event records' components (all but
 *       {@code emittedAt}) plus {@code eventId} and {@code eventType}. Adding a field to the records adds it here.</li>
 *   <li><b>JSON form</b>, from each component's type: text for {@code String}, a canonical lower-case UUID for
 *       {@code UUID}, a canonical ISO-8601 UTC instant ({@code Instant.toString}) for {@code Instant}, a whole number
 *       for {@code long}, and an object of scope to level, by their wire names, for the permissions.</li>
 *   <li><b>Values</b>: the body is read back into its contract record, so the record's own rules
 *       ({@code IntegrationEventRules}: required fields, sizes, {@code version} at least 1, timestamps in order,
 *       {@code INGESTION} only {@code WRITE}) are the one place those rules live.</li>
 * </ol>
 * <p>Scopes and levels are written by {@link ApiKeyScope#wireName()} and {@link ApiKeyAccessLevel#wireName()},
 * never by the Java constant name. Key order is not part of the contract. The golden files in
 * {@code src/test/resources/contracts/apikey.projection} pin the shape.</p>
 */
@Component
@RequiredArgsConstructor
public class IntegrationEventSerializer {
    static final String EVENT_ID = "eventId";
    static final String EVENT_TYPE = "eventType";
    static final String EMITTED_AT = "emittedAt";
    static final String PERMISSIONS = "permissions";

    /** The contract record behind each event type. */
    private static final Map<String, Class<? extends ApiKeyIntegrationEvent>> EVENT_CLASSES = Map.of(
            ApiKeyCreatedIntegrationEvent.EVENT_TYPE, ApiKeyCreatedIntegrationEvent.class,
            ApiKeyUpdatedIntegrationEvent.EVENT_TYPE, ApiKeyUpdatedIntegrationEvent.class,
            ApiKeyDeletedIntegrationEvent.EVENT_TYPE, ApiKeyDeletedIntegrationEvent.class);

    /** The event fields a stored body carries, by name and Java type: every record component but emittedAt. */
    private static final Map<String, Class<?>> FIELDS = storedFields();

    /** Every key a stored body carries, nullable or not. */
    static final Set<String> STORED_KEYS = storedKeys();

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
            fields.set(PERMISSIONS, wirePermissions(event.permissions()));
            body.setAll(fields);
            validate(body, Optional.of(eventId.toString()));
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
            validate(body, Optional.empty());
            body.put(EMITTED_AT, emittedAt.toString());
            return json.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IntegrationEventSerializationException("The outbox payload is not valid JSON", e);
        }
    }

    /** Throws unless the body is exactly a stored integration event, and returns it; {@code expectedEventId} is checked when given. */
    ApiKeyIntegrationEvent validate(ObjectNode body, Optional<String> expectedEventId) {
        // 1. Keys
        for (Map.Entry<String, JsonNode> field : body.properties())
            if (!STORED_KEYS.contains(field.getKey()))
                throw invalid(field.getKey(), "is not part of the event contract");
        for (String key : STORED_KEYS)
            if (!body.has(key))
                throw invalid(key, "is missing");

        String eventId = uuid(EVENT_ID, body.get(EVENT_ID));
        expectedEventId.filter(expected -> !expected.equals(eventId)).ifPresent(expected -> {
            throw invalid(EVENT_ID, "is " + eventId + " but the event was given " + expected);
        });
        String eventType = text(EVENT_TYPE, body.get(EVENT_TYPE));
        Class<? extends ApiKeyIntegrationEvent> eventClass = Optional.ofNullable(EVENT_CLASSES.get(eventType))
                .orElseThrow(() -> invalid(EVENT_TYPE, "is " + eventType + ", expected one of " + EVENT_CLASSES.keySet()));

        // 2. JSON form, field by field
        ObjectNode fields = body.deepCopy();
        fields.remove(EVENT_ID);
        fields.remove(EVENT_TYPE);
        FIELDS.forEach((name, type) -> checkForm(name, type, fields.get(name)));
        fields.set(PERMISSIONS, javaPermissions(fields.get(PERMISSIONS)));

        // 3. Values: the contract record's own rules
        return readAs(eventClass, fields);
    }

    /** Reads the fields into the contract record; its compact constructor refuses anything the contract forbids. */
    private ApiKeyIntegrationEvent readAs(Class<? extends ApiKeyIntegrationEvent> eventClass, ObjectNode fields) {
        try {
            return json.treeToValue(fields, eventClass);
        } catch (JacksonException e) {
            Throwable cause = e;
            while (cause != null && !(cause instanceof IllegalArgumentException))
                cause = cause.getCause();
            if (cause != null)
                throw new IntegrationEventSerializationException(cause.getMessage(), e);
            throw new IntegrationEventSerializationException("The event cannot be read as its contract record", e);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // JSON form
    // ---------------------------------------------------------------------------------------------------------------

    /** Null is left to the record, which knows which fields may be null; anything else must have its type's form. */
    private static void checkForm(String name, Class<?> type, JsonNode value) {
        if (value.isNull()) {
            if (type.isPrimitive())
                throw invalid(name, "is null");
            return;
        }
        if (type == String.class)
            text(name, value);
        else if (type == UUID.class)
            uuid(name, value);
        else if (type == Instant.class)
            instant(name, value);
        else if (type == long.class || type == Long.class)
            wholeNumber(name, value);
        else if (type == Map.class)
            wirePermissionsForm(name, value);
        else
            throw new IllegalStateException("The serializer has no JSON form for " + name + " of type " + type.getName()
                    + "; add one before publishing that field");
    }

    private static String text(String name, JsonNode value) {
        if (value.isNull())
            throw invalid(name, "is null");
        if (!value.isString())
            throw invalid(name, "is not text");
        return value.asString();
    }

    private static String uuid(String name, JsonNode value) {
        String text = text(name, value);
        try {
            if (UUID.fromString(text).toString().equals(text))
                return text;
        } catch (IllegalArgumentException ignored) {
            // reported below
        }
        throw invalid(name, "is not a canonical lower-case UUID: " + text);
    }

    static void instant(String name, JsonNode value) {
        String text = text(name, value);
        try {
            if (Instant.parse(text).toString().equals(text))
                return;
        } catch (DateTimeParseException ignored) {
            // reported below
        }
        throw invalid(name, "is not an ISO-8601 UTC instant (as Instant.toString writes it): " + text);
    }

    private static void wholeNumber(String name, JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong())
            throw invalid(name, "is not a whole number");
    }

    /** An object of known scope to known level, by wire name; no access is a missing scope. */
    private static void wirePermissionsForm(String name, JsonNode permissions) {
        if (!permissions.isObject())
            throw invalid(name, "is not an object of scope to level");
        for (Map.Entry<String, JsonNode> grant : permissions.properties()) {
            if (ApiKeyScope.fromWire(grant.getKey()).isEmpty())
                throw invalid(name, "has unknown scope " + grant.getKey());
            JsonNode level = grant.getValue();
            if (!level.isString() || ApiKeyAccessLevel.fromWire(level.asString()).isEmpty())
                throw invalid(name, "has " + grant.getKey() + " set to " + level
                        + ", which is not a level; no access is a missing scope");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Wire names
    // ---------------------------------------------------------------------------------------------------------------

    private ObjectNode wirePermissions(Map<ApiKeyScope, ApiKeyAccessLevel> permissions) {
        ObjectNode wire = json.createObjectNode();
        permissions.forEach((scope, level) -> wire.put(scope.wireName(), level.wireName()));
        return wire;
    }

    /** Wire names to the record's Java names, for reading back; the form was already checked. Null stays null. */
    private JsonNode javaPermissions(JsonNode wire) {
        if (wire.isNull())
            return wire;
        ObjectNode java = json.createObjectNode();
        for (Map.Entry<String, JsonNode> grant : wire.properties())
            java.put(ApiKeyScope.fromWire(grant.getKey()).orElseThrow().name(),
                    ApiKeyAccessLevel.fromWire(grant.getValue().asString()).orElseThrow().name());
        return java;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The key set, derived from the records
    // ---------------------------------------------------------------------------------------------------------------

    /** The created event's fields; the other events must carry exactly the same, or the class fails to load. */
    private static Map<String, Class<?>> storedFields() {
        Map<String, Class<?>> fields = fieldsOf(ApiKeyCreatedIntegrationEvent.class);
        for (Class<? extends ApiKeyIntegrationEvent> eventClass : EVENT_CLASSES.values())
            if (!fieldsOf(eventClass).equals(fields))
                throw new IllegalStateException("Every apiKey.projection event must carry the same fields; "
                        + eventClass.getSimpleName() + " has " + fieldsOf(eventClass) + ", expected " + fields);
        return Map.copyOf(fields);
    }

    private static Map<String, Class<?>> fieldsOf(Class<? extends ApiKeyIntegrationEvent> eventClass) {
        Map<String, Class<?>> fields = new LinkedHashMap<>();
        for (RecordComponent component : eventClass.getRecordComponents())
            if (!component.getName().equals(EMITTED_AT))
                fields.put(component.getName(), component.getType());
        return fields;
    }

    private static Set<String> storedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(EVENT_ID);
        keys.add(EVENT_TYPE);
        keys.addAll(FIELDS.keySet());
        return Set.copyOf(keys);
    }

    private static IntegrationEventSerializationException invalid(String field, String problem) {
        return new IntegrationEventSerializationException("Integration event field " + field + " " + problem);
    }
}
