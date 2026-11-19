package me.dhiren9939.backend.access.infrastructure.serialization;

import me.dhiren9939.backend.common.access.events.outbound.ApiKeyAccessLevel;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyScope;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

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
    void anyOtherNullIsRejectedNamingTheField() {
        assertThatThrownBy(() -> serializer.toJson(EVENT_ID, updated().accessSecretHash(null).build()))
                .isInstanceOf(IntegrationEventSerializationException.class)
                .hasMessageContaining("accessSecretHash");
        assertThatThrownBy(() -> serializer.toJson(EVENT_ID, updated().permissions(null).build()))
                .isInstanceOf(IntegrationEventSerializationException.class)
                .hasMessageContaining("permissions");
        assertThatThrownBy(() -> serializer.toJson(EVENT_ID, updated().apiKeyName(null).build()))
                .isInstanceOf(IntegrationEventSerializationException.class)
                .hasMessageContaining("apiKeyName");
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
