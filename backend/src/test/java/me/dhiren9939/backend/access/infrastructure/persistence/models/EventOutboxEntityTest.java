package me.dhiren9939.backend.access.infrastructure.persistence.models;


import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins down how integration events map to outbox rows. */
class EventOutboxEntityTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:00:00Z");
    private static final UUID API_KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Map<String, String> PERMISSIONS = new LinkedHashMap<>();

    static {
        PERMISSIONS.put("INGESTION", "WRITE");
        PERMISSIONS.put("TENANT", "READ");
    }

    @Test
    void createdEventBecomesARowWithTheSecretHash() {
        EventOutboxEntity row = EventOutboxEntity.of(ApiKeyCreatedIntegrationEvent.builder()
                .apiKeyName("test-key").apiKeyId(API_KEY_ID).version(1).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc").accessSecretHash("hashed_secret")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build());
        assertThat(row.getEventType()).isEqualTo("apiKey.projection.create");
        assertThat(row.getApiKeyId()).isEqualTo(API_KEY_ID);
        assertThat(row.getVersion()).isEqualTo(1);
        assertThat(row.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(row.getEmittedAt()).isNull();
        assertThat(row.getUserId()).isEqualTo(USER_ID);
        assertThat(row.getAccessId()).isEqualTo("V1_acc");
        assertThat(row.getAccessSecretHash()).isEqualTo("hashed_secret");
        assertThat(row.getApiKeyName()).isEqualTo("test-key");
        assertThat(row.getPermissions()).isEqualTo("{\"INGESTION\":\"WRITE\",\"TENANT\":\"READ\"}");
        assertThat(row.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void updatedEventBecomesARowWithTheSecretHash() {
        EventOutboxEntity row = EventOutboxEntity.of(ApiKeyUpdatedIntegrationEvent.builder()
                .apiKeyName("renamed").apiKeyId(API_KEY_ID).version(2).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc").accessSecretHash("hashed_secret")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build());
        assertThat(row.getEventType()).isEqualTo("apiKey.projection.update");
        assertThat(row.getVersion()).isEqualTo(2);
        assertThat(row.getApiKeyName()).isEqualTo("renamed");
        assertThat(row.getAccessSecretHash()).isEqualTo("hashed_secret");
    }

    @Test
    void deletedEventBecomesARowWithTheSecretHash() {
        EventOutboxEntity row = EventOutboxEntity.of(ApiKeyDeletedIntegrationEvent.builder()
                .apiKeyName("test-key").apiKeyId(API_KEY_ID).version(2).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc").accessSecretHash("hashed_secret")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build());
        assertThat(row.getEventType()).isEqualTo("apiKey.projection.delete");
        assertThat(row.getVersion()).isEqualTo(2);
        assertThat(row.getAccessSecretHash()).isEqualTo("hashed_secret");
    }

    @Test
    void permissionsAreStoredAsAJsonObjectKeyedByScopeWithAbsentMeaningNone() {
        assertThat(EventOutboxEntity.permissionsJson(Map.of())).isEqualTo("{}");
        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put("CHANNEL", "READ_WRITE");
        permissions.put("INGESTION", "WRITE");
        assertThat(EventOutboxEntity.permissionsJson(permissions))
                .isEqualTo("{\"CHANNEL\":\"READ_WRITE\",\"INGESTION\":\"WRITE\"}");
    }

}
