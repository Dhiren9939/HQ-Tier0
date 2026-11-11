package me.dhiren9939.backend.access.infrastructure.persistence.models;



import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.events.domain.CreateApiKeyProjectionCommand;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;
import me.dhiren9939.backend.shared.access.events.domain.DeleteApiKeyProjectionCommand;
import me.dhiren9939.backend.shared.access.events.domain.UpdateApiKeyProjectionCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins down how domain events map to outbox rows. */
class EventOutboxEntityTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:00:00Z");
    private static final UUID API_KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Permissions PERMISSIONS = Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE).with(Scope.TENANT, AccessLevel.READ);

    @Test
    void createdEventBecomesARowWithTheSecretHash() {
        EventOutboxEntity row = EventOutboxEntity.of(CreateApiKeyProjectionCommand.builder()
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
    void updatedEventBecomesARowWithoutTheSecretHash() {
        EventOutboxEntity row = EventOutboxEntity.of(UpdateApiKeyProjectionCommand.builder()
                .apiKeyName("renamed").apiKeyId(API_KEY_ID).version(2).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build());
        assertThat(row.getEventType()).isEqualTo("apiKey.projection.update");
        assertThat(row.getVersion()).isEqualTo(2);
        assertThat(row.getApiKeyName()).isEqualTo("renamed");
        assertThat(row.getAccessSecretHash()).isNull();
    }

    @Test
    void deletedEventBecomesARowWithoutTheSecretHash() {
        EventOutboxEntity row = EventOutboxEntity.of(DeleteApiKeyProjectionCommand.builder()
                .apiKeyName("test-key").apiKeyId(API_KEY_ID).version(2).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build());
        assertThat(row.getEventType()).isEqualTo("apiKey.projection.delete");
        assertThat(row.getVersion()).isEqualTo(2);
        assertThat(row.getAccessSecretHash()).isNull();
    }

    @Test
    void permissionsAreStoredAsAJsonObjectKeyedByScopeWithAbsentMeaningNone() {
        assertThat(EventOutboxEntity.permissionsJson(Permissions.none())).isEqualTo("{}");
        assertThat(EventOutboxEntity.permissionsJson(
                Permissions.none().with(Scope.CHANNEL, AccessLevel.READ_WRITE).with(Scope.INGESTION, AccessLevel.WRITE)))
                .isEqualTo("{\"INGESTION\":\"WRITE\",\"CHANNEL\":\"READ_WRITE\"}");
    }

}
