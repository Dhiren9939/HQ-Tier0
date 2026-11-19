package me.dhiren9939.backend.access.infrastructure.serialization;

import me.dhiren9939.backend.common.access.events.outbound.ApiKeyAccessLevel;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyScope;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The JSON shape of the outbox payload and of the published body. Key order is not part of the contract. */
class IntegrationEventSerializerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private final IntegrationEventSerializer serializer = new IntegrationEventSerializer(JSON);

    private static ApiKeyUpdatedIntegrationEvent.ApiKeyUpdatedIntegrationEventBuilder updated() {
        Map<ApiKeyScope, ApiKeyAccessLevel> permissions = new EnumMap<>(ApiKeyScope.class);
        permissions.put(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE);
        permissions.put(ApiKeyScope.TENANT, ApiKeyAccessLevel.READ_WRITE);
        return ApiKeyUpdatedIntegrationEvent.builder()
                .apiKeyName("orders")
                .apiKeyId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .version(3)
                .occurredAt(Instant.parse("2026-01-15T12:00:00.123456Z"))
                .userId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .accessId("V1_0Ab-CdEf_gHiJ")
                .accessSecretHash("abc123")
                .permissions(permissions)
                .createdAt(Instant.parse("2026-01-15T11:00:00Z"))
                .expiresAt(Instant.parse("2026-02-15T11:00:00Z"));
    }

    private JsonNode body(ApiKeyUpdatedIntegrationEvent event) {
        return JSON.readTree(serializer.toJson(EVENT_ID, event));
    }

    @Test
    void theBodyIsEverythingButEmittedAt() {
        JsonNode expected = JSON.readTree("""
                {
                  "eventId": "00000000-0000-0000-0000-0000000000e1",
                  "eventType": "apiKey.projection.update",
                  "apiKeyName": "orders",
                  "apiKeyId": "00000000-0000-0000-0000-000000000002",
                  "version": 3,
                  "occurredAt": "2026-01-15T12:00:00.123456Z",
                  "userId": "00000000-0000-0000-0000-000000000001",
                  "accessId": "V1_0Ab-CdEf_gHiJ",
                  "accessSecretHash": "abc123",
                  "permissions": {"INGESTION": "WRITE", "TENANT": "READ_WRITE"},
                  "createdAt": "2026-01-15T11:00:00Z",
                  "expiresAt": "2026-02-15T11:00:00Z"
                }""");

        assertThat(body(updated().build())).isEqualTo(expected);
    }

    @Test
    void everyEventTypeIsWrittenWithItsOwnType() {
        ApiKeyUpdatedIntegrationEvent base = updated().build();
        ApiKeyCreatedIntegrationEvent created = ApiKeyCreatedIntegrationEvent.builder()
                .apiKeyName(base.apiKeyName()).apiKeyId(base.apiKeyId()).version(1).occurredAt(base.occurredAt())
                .userId(base.userId()).accessId(base.accessId()).accessSecretHash("h").permissions(base.permissions())
                .createdAt(base.createdAt()).build();
        ApiKeyDeletedIntegrationEvent deleted = ApiKeyDeletedIntegrationEvent.builder()
                .apiKeyName(base.apiKeyName()).apiKeyId(base.apiKeyId()).version(2).occurredAt(base.occurredAt())
                .userId(base.userId()).accessId(base.accessId()).accessSecretHash("h").permissions(base.permissions())
                .createdAt(base.createdAt()).build();

        assertThat(JSON.readTree(serializer.toJson(EVENT_ID, created)).get("eventType").asString())
                .isEqualTo("apiKey.projection.create");
        assertThat(JSON.readTree(serializer.toJson(EVENT_ID, deleted)).get("eventType").asString())
                .isEqualTo("apiKey.projection.delete");
    }

    @Test
    void expiresAtIsTheOnlyKeyThatMayBeNullAndItIsKept() {
        JsonNode body = body(updated().expiresAt(null).build());

        assertThat(body.has("expiresAt")).isTrue();
        assertThat(body.get("expiresAt").isNull()).isTrue();
        assertThat(body.properties().stream().filter(field -> field.getValue().isNull()).map(Map.Entry::getKey))
                .containsExactly("expiresAt");
    }

    @Test
    void emptyPermissionsAreAnEmptyObject() {
        JsonNode permissions = body(updated().permissions(Map.of()).build()).get("permissions");

        assertThat(permissions.isObject()).isTrue();
        assertThat(permissions.size()).isZero();
    }

    @Test
    void anEventThatIsAlreadyEmittedIsRejected() {
        assertThatThrownBy(() -> serializer.toJson(EVENT_ID, updated().emittedAt(Instant.parse("2026-01-15T12:00:01Z")).build()))
                .isInstanceOf(IntegrationEventSerializationException.class)
                .hasMessageContaining("emittedAt");
    }

    @Test
    void aMissingEventOrEventIdIsRejected() {
        assertThatThrownBy(() -> serializer.toJson(null, updated().build()))
                .isInstanceOf(IntegrationEventSerializationException.class).hasMessageContaining("eventId");
        assertThatThrownBy(() -> serializer.toJson(EVENT_ID, null))
                .isInstanceOf(IntegrationEventSerializationException.class);
    }

    @Test
    void theEventIdIsWrittenInItsCanonicalLowerCaseForm() {
        assertThat(body(updated().build()).get("eventId").asString()).isEqualTo(EVENT_ID.toString());
    }

    // --- the stored payload is checked again before it is published: a row nothing here wrote cannot be sent ----

    private static Arguments broken(String field, Consumer<ObjectNode> change) {
        return Arguments.of(field, change);
    }

    static Stream<Arguments> brokenPayloads() {
        return Stream.of(
                broken("extra", b -> b.put("extra", "x")),
                broken("emittedAt", b -> b.put("emittedAt", "2026-01-15T12:00:00Z")),
                broken("eventId", b -> b.remove("eventId")),
                broken("eventId", b -> b.putNull("eventId")),
                broken("eventId", b -> b.put("eventId", "not-a-uuid")),
                broken("eventId", b -> b.put("eventId", "00000000-0000-0000-0000-0000000000E1")),
                broken("eventId", b -> b.put("eventId", 5)),
                broken("eventType", b -> b.put("eventType", "apiKey.created")),
                broken("eventType", b -> b.remove("eventType")),
                broken("apiKeyName", b -> b.remove("apiKeyName")),
                broken("apiKeyName", b -> b.putNull("apiKeyName")),
                broken("apiKeyName", b -> b.put("apiKeyName", "")),
                broken("apiKeyName", b -> b.put("apiKeyName", " ")),
                broken("apiKeyName", b -> b.put("apiKeyName", "x".repeat(257))),
                broken("apiKeyName", b -> b.put("apiKeyName", 7)),
                broken("apiKeyId", b -> b.put("apiKeyId", "00000000-0000-0000-0000-00000000000G")),
                broken("apiKeyId", b -> b.remove("apiKeyId")),
                broken("userId", b -> b.putNull("userId")),
                broken("userId", b -> b.put("userId", "1")),
                broken("accessId", b -> b.put("accessId", "x".repeat(257))),
                broken("accessId", b -> b.remove("accessId")),
                broken("accessSecretHash", b -> b.putNull("accessSecretHash")),
                broken("accessSecretHash", b -> b.put("accessSecretHash", "")),
                broken("accessSecretHash", b -> b.remove("accessSecretHash")),
                broken("version", b -> b.put("version", 0)),
                broken("version", b -> b.put("version", -3)),
                broken("version", b -> b.put("version", 1.5)),
                broken("version", b -> b.put("version", "3")),
                broken("version", b -> b.putNull("version")),
                broken("version", b -> b.remove("version")),
                broken("occurredAt", b -> b.put("occurredAt", "2026-01-15T12:00:00+01:00")),
                broken("occurredAt", b -> b.put("occurredAt", "2026-01-15T12:00:00.000Z")),
                broken("occurredAt", b -> b.put("occurredAt", "2026-01-15 12:00:00")),
                broken("occurredAt", b -> b.put("occurredAt", 1768478400)),
                broken("occurredAt", b -> b.putNull("occurredAt")),
                broken("occurredAt", b -> b.remove("occurredAt")),
                broken("createdAt", b -> b.put("createdAt", "yesterday")),
                broken("createdAt", b -> b.putNull("createdAt")),
                broken("expiresAt", b -> b.put("expiresAt", "2026-02-15")),
                broken("expiresAt", b -> b.put("expiresAt", 0)),
                broken("expiresAt", b -> b.remove("expiresAt")),
                broken("permissions", b -> b.remove("permissions")),
                broken("permissions", b -> b.putNull("permissions")),
                broken("permissions", b -> b.put("permissions", "INGESTION")),
                broken("permissions", b -> b.putArray("permissions")),
                broken("permissions", b -> b.putObject("permissions").put("BILLING", "READ")),
                broken("permissions", b -> b.putObject("permissions").put("ingestion", "WRITE")),
                broken("permissions", b -> b.putObject("permissions").put("TENANT", "NONE")),
                broken("permissions", b -> b.putObject("permissions").put("TENANT", "ADMIN")),
                broken("permissions", b -> b.putObject("permissions").putNull("TENANT")),
                broken("permissions", b -> b.putObject("permissions").put("TENANT", 1)),
                broken("permissions", b -> b.putObject("permissions").put("INGESTION", "READ")),
                broken("permissions", b -> b.putObject("permissions").put("INGESTION", "READ_WRITE")));
    }

    @ParameterizedTest(name = "{0}: {index}")
    @MethodSource("brokenPayloads")
    void aStoredPayloadThatBreaksTheContractIsNeverPublished(String field, Consumer<ObjectNode> change) {
        ObjectNode payload = (ObjectNode) JSON.readTree(serializer.toJson(EVENT_ID, updated().build()));
        change.accept(payload);

        assertThatThrownBy(() -> serializer.withEmittedAt(payload.toString(), Instant.parse("2026-01-15T12:00:05Z")))
                .isInstanceOf(IntegrationEventSerializationException.class)
                .hasMessageContaining(field);
    }

    @Test
    void everyValidPermissionCombinationIsWrittenAndPublishable() {
        for (ApiKeyScope scope : ApiKeyScope.values())
            for (ApiKeyAccessLevel level : ApiKeyAccessLevel.values()) {
                if (scope == ApiKeyScope.INGESTION && level != ApiKeyAccessLevel.WRITE)
                    continue;
                String stored = serializer.toJson(EVENT_ID, updated().permissions(Map.of(scope, level)).build());

                assertThat(JSON.readTree(serializer.withEmittedAt(stored, Instant.parse("2026-01-15T12:00:05Z")))
                        .get("permissions").get(scope.name()).asString()).isEqualTo(level.name());
            }
    }

    @Test
    void aWrittenBodyAlwaysPassesThePublishCheck() {
        for (String expiry : new String[]{null, "2026-02-15T11:00:00Z", "2026-02-15T11:00:00.5Z", "2999-12-31T23:59:59.999999999Z"}) {
            String stored = serializer.toJson(EVENT_ID, updated().expiresAt(expiry == null ? null : Instant.parse(expiry)).build());

            assertThat(serializer.withEmittedAt(stored, Instant.parse("2026-01-15T12:00:05Z"))).contains("emittedAt");
        }
    }

    @Test
    void withEmittedAtAddsOnlyThatTimeToTheStoredBody() {
        String stored = serializer.toJson(EVENT_ID, updated().build());

        JsonNode published = JSON.readTree(serializer.withEmittedAt(stored, Instant.parse("2026-01-15T12:00:05Z")));

        assertThat(published.get("emittedAt").asString()).isEqualTo("2026-01-15T12:00:05Z");
        ((tools.jackson.databind.node.ObjectNode) published).remove("emittedAt");
        assertThat(published).isEqualTo(JSON.readTree(stored));
    }

    @Test
    void aPayloadThatIsNotAJsonObjectCannotBePublished() {
        Instant at = Instant.parse("2026-01-15T12:00:05Z");

        assertThatThrownBy(() -> serializer.withEmittedAt("[1]", at))
                .isInstanceOf(IntegrationEventSerializationException.class);
        assertThatThrownBy(() -> serializer.withEmittedAt("not json", at))
                .isInstanceOf(IntegrationEventSerializationException.class);
    }
}
