package me.dhiren9939.backend.access.application.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import me.dhiren9939.backend.access.domain.events.ApiKeyCreated;
import me.dhiren9939.backend.access.domain.events.ApiKeyDeleted;
import me.dhiren9939.backend.access.domain.events.ApiKeyUpdated;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The default mapper is the only seam between the Access domain and the published integration events. */
class DefaultApiKeyIntegrationEventMapperTest {

    private static final ApiKeyIntegrationEventMapper MAPPER = new DefaultApiKeyIntegrationEventMapper();

    private static final UUID KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-02-15T11:00:00Z");
    private static final String NAME = "test-key";
    private static final String ACCESS_ID = "V1_acc";
    private static final Permissions PERMISSIONS = Permissions.none()
            .with(Scope.INGESTION, AccessLevel.WRITE)
            .with(Scope.TENANT, AccessLevel.READ);

    private static Map<String, String> wirePermissions() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("INGESTION", "WRITE");
        expected.put("TENANT", "READ");
        return expected;
    }

    @Test
    void createdEventBecomesACreatedIntegrationEvent() {
        ApiKeyCreated event = new ApiKeyCreated(NAME, KEY_ID, 1, OCCURRED_AT, USER_ID, ACCESS_ID,
                "hashed_secret", PERMISSIONS, CREATED_AT, EXPIRES_AT);

        ApiKeyCreatedIntegrationEvent integrationEvent = (ApiKeyCreatedIntegrationEvent) MAPPER.toIntegrationEvent(event);

        assertThat(integrationEvent.eventType()).isEqualTo("apiKey.projection.create");
        assertThat(integrationEvent.apiKeyName()).isEqualTo(NAME);
        assertThat(integrationEvent.apiKeyId()).isEqualTo(KEY_ID);
        assertThat(integrationEvent.version()).isEqualTo(1);
        assertThat(integrationEvent.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(integrationEvent.emittedAt()).as("only the relay stamps emission").isNull();
        assertThat(integrationEvent.userId()).isEqualTo(USER_ID);
        assertThat(integrationEvent.accessId()).isEqualTo(ACCESS_ID);
        assertThat(integrationEvent.accessSecretHash()).isEqualTo("hashed_secret");
        assertThat(integrationEvent.permissions()).containsExactlyEntriesOf(wirePermissions());
        assertThat(integrationEvent.createdAt()).isEqualTo(CREATED_AT);
        assertThat(integrationEvent.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void updatedEventBecomesAnUpdatedIntegrationEvent() {
        ApiKeyUpdated event = new ApiKeyUpdated(NAME, KEY_ID, 2, OCCURRED_AT, USER_ID, ACCESS_ID,
                "hashed_secret", PERMISSIONS, CREATED_AT, EXPIRES_AT);

        ApiKeyUpdatedIntegrationEvent integrationEvent = (ApiKeyUpdatedIntegrationEvent) MAPPER.toIntegrationEvent(event);

        assertThat(integrationEvent.eventType()).isEqualTo("apiKey.projection.update");
        assertThat(integrationEvent.version()).isEqualTo(2);
        assertThat(integrationEvent.emittedAt()).isNull();
        assertThat(integrationEvent.accessSecretHash()).as("every command is a full snapshot").isEqualTo("hashed_secret");
        assertThat(integrationEvent.permissions()).containsExactlyEntriesOf(wirePermissions());
        assertThat(integrationEvent.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void deletedEventBecomesADeletedIntegrationEvent() {
        ApiKeyDeleted event = new ApiKeyDeleted(NAME, KEY_ID, 3, OCCURRED_AT, USER_ID, ACCESS_ID,
                "hashed_secret", PERMISSIONS, CREATED_AT, EXPIRES_AT);

        ApiKeyDeletedIntegrationEvent integrationEvent = (ApiKeyDeletedIntegrationEvent) MAPPER.toIntegrationEvent(event);

        assertThat(integrationEvent.eventType()).isEqualTo("apiKey.projection.delete");
        assertThat(integrationEvent.version()).isEqualTo(3);
        assertThat(integrationEvent.emittedAt()).isNull();
        assertThat(integrationEvent.accessSecretHash()).as("every command is a full snapshot").isEqualTo("hashed_secret");
        assertThat(integrationEvent.permissions()).containsExactlyEntriesOf(wirePermissions());
        assertThat(integrationEvent.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void wirePermissionsUseScopeDeclarationOrderAndOmitNone() {
        Map<String, String> wire = WirePermissions.of(PERMISSIONS);

        assertThat(wire.keySet()).containsExactly("INGESTION", "TENANT");
        assertThat(wire).containsExactlyEntriesOf(wirePermissions());
    }

    @Test
    void wirePermissionsAreUnmodifiable() {
        Map<String, String> wire = WirePermissions.of(PERMISSIONS);

        assertThatThrownBy(() -> wire.put("CHANNEL", "READ"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void wirePermissionNamesAreTheContract() {
        assertThat(java.util.Arrays.stream(Scope.values()).map(Enum::name))
                .containsExactly("INGESTION", "TENANT", "CHANNEL");
        assertThat(java.util.Arrays.stream(AccessLevel.values()).map(Enum::name))
                .containsExactly("NONE", "READ", "WRITE", "READ_WRITE");
    }
}
