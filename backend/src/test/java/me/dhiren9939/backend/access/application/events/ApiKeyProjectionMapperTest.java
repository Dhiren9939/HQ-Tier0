package me.dhiren9939.backend.access.application.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import me.dhiren9939.backend.access.domain.events.ApiKeyCreated;
import me.dhiren9939.backend.access.domain.events.ApiKeyDeleted;
import me.dhiren9939.backend.access.domain.events.ApiKeyUpdated;
import me.dhiren9939.backend.common.access.events.outbound.CreateApiKeyProjectionCommand;
import me.dhiren9939.backend.common.access.events.outbound.DeleteApiKeyProjectionCommand;
import me.dhiren9939.backend.common.access.events.outbound.UpdateApiKeyProjectionCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The mapper is the only seam between the Access domain and the published projection contract. */
class ApiKeyProjectionMapperTest {

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
    void createdEventBecomesCreateCommand() {
        ApiKeyCreated event = new ApiKeyCreated(NAME, KEY_ID, 1, OCCURRED_AT, USER_ID, ACCESS_ID,
                "hashed_secret", PERMISSIONS, CREATED_AT, EXPIRES_AT);

        CreateApiKeyProjectionCommand command = (CreateApiKeyProjectionCommand) ApiKeyProjectionMapper.toProjectionCommand(event);

        assertThat(command.eventType()).isEqualTo("apiKey.projection.create");
        assertThat(command.apiKeyName()).isEqualTo(NAME);
        assertThat(command.apiKeyId()).isEqualTo(KEY_ID);
        assertThat(command.version()).isEqualTo(1);
        assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(command.emittedAt()).as("only the relay stamps emission").isNull();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.accessId()).isEqualTo(ACCESS_ID);
        assertThat(command.accessSecretHash()).isEqualTo("hashed_secret");
        assertThat(command.permissions()).containsExactlyEntriesOf(wirePermissions());
        assertThat(command.createdAt()).isEqualTo(CREATED_AT);
        assertThat(command.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void updatedEventBecomesUpdateCommand() {
        ApiKeyUpdated event = new ApiKeyUpdated(NAME, KEY_ID, 2, OCCURRED_AT, USER_ID, ACCESS_ID,
                "hashed_secret", PERMISSIONS, CREATED_AT, EXPIRES_AT);

        UpdateApiKeyProjectionCommand command = (UpdateApiKeyProjectionCommand) ApiKeyProjectionMapper.toProjectionCommand(event);

        assertThat(command.eventType()).isEqualTo("apiKey.projection.update");
        assertThat(command.version()).isEqualTo(2);
        assertThat(command.emittedAt()).isNull();
        assertThat(command.accessSecretHash()).as("every command is a full snapshot").isEqualTo("hashed_secret");
        assertThat(command.permissions()).containsExactlyEntriesOf(wirePermissions());
        assertThat(command.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void deletedEventBecomesDeleteCommand() {
        ApiKeyDeleted event = new ApiKeyDeleted(NAME, KEY_ID, 3, OCCURRED_AT, USER_ID, ACCESS_ID,
                "hashed_secret", PERMISSIONS, CREATED_AT, EXPIRES_AT);

        DeleteApiKeyProjectionCommand command = (DeleteApiKeyProjectionCommand) ApiKeyProjectionMapper.toProjectionCommand(event);

        assertThat(command.eventType()).isEqualTo("apiKey.projection.delete");
        assertThat(command.version()).isEqualTo(3);
        assertThat(command.emittedAt()).isNull();
        assertThat(command.accessSecretHash()).as("every command is a full snapshot").isEqualTo("hashed_secret");
        assertThat(command.permissions()).containsExactlyEntriesOf(wirePermissions());
        assertThat(command.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void wirePermissionsUseScopeDeclarationOrderAndOmitNone() {
        Map<String, String> wire = ApiKeyProjectionMapper.toWirePermissions(PERMISSIONS);

        assertThat(wire.keySet()).containsExactly("INGESTION", "TENANT");
        assertThat(wire).containsExactlyEntriesOf(wirePermissions());
    }

    @Test
    void wirePermissionsAreUnmodifiable() {
        Map<String, String> wire = ApiKeyProjectionMapper.toWirePermissions(PERMISSIONS);

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
