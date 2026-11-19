package me.dhiren9939.backend.access.application.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import me.dhiren9939.backend.access.domain.events.ApiKeyCreated;
import me.dhiren9939.backend.access.domain.events.ApiKeyDeleted;
import me.dhiren9939.backend.access.domain.events.ApiKeyUpdated;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyAccessLevel;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyScope;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    private static Map<ApiKeyScope, ApiKeyAccessLevel> publishedPermissions() {
        return Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE, ApiKeyScope.TENANT, ApiKeyAccessLevel.READ);
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
        assertThat(integrationEvent.emittedAt()).as("only publishing stamps emission").isNull();
        assertThat(integrationEvent.userId()).isEqualTo(USER_ID);
        assertThat(integrationEvent.accessId()).isEqualTo(ACCESS_ID);
        assertThat(integrationEvent.accessSecretHash()).isEqualTo("hashed_secret");
        assertThat(integrationEvent.permissions()).containsExactlyEntriesOf(publishedPermissions());
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
        assertThat(integrationEvent.permissions()).containsExactlyEntriesOf(publishedPermissions());
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
        assertThat(integrationEvent.permissions()).containsExactlyEntriesOf(publishedPermissions());
        assertThat(integrationEvent.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void permissionsAreOrderedByScopeAndOmitNone() {
        Map<ApiKeyScope, ApiKeyAccessLevel> published = MAPPER.toIntegrationEvent(
                new ApiKeyCreated(NAME, KEY_ID, 1, OCCURRED_AT, USER_ID, ACCESS_ID, "h", PERMISSIONS, CREATED_AT, null))
                .permissions();

        assertThat(published.keySet()).containsExactly(ApiKeyScope.INGESTION, ApiKeyScope.TENANT);
        assertThat(published).doesNotContainKey(ApiKeyScope.CHANNEL);
    }

    @Test
    void noPermissionsArePublishedAsAnEmptyMap() {
        assertThat(MAPPER.toIntegrationEvent(new ApiKeyCreated(NAME, KEY_ID, 1, OCCURRED_AT, USER_ID, ACCESS_ID, "h",
                Permissions.none(), CREATED_AT, null)).permissions()).isEmpty();
    }

    @Test
    void publishedPermissionsAreUnmodifiable() {
        Map<ApiKeyScope, ApiKeyAccessLevel> published = MAPPER.toIntegrationEvent(
                new ApiKeyCreated(NAME, KEY_ID, 1, OCCURRED_AT, USER_ID, ACCESS_ID, "h", PERMISSIONS, CREATED_AT, null))
                .permissions();

        assertThatThrownBy(() -> published.put(ApiKeyScope.CHANNEL, ApiKeyAccessLevel.READ))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void everyGrantableScopeAndLevelHasAPublishedCounterpartOfTheSameName() {
        for (Scope scope : Scope.values())
            for (AccessLevel level : scope.allowedLevels()) {
                if (level == AccessLevel.NONE)
                    continue;
                Map<ApiKeyScope, ApiKeyAccessLevel> published = MAPPER.toIntegrationEvent(new ApiKeyCreated(NAME, KEY_ID,
                        1, OCCURRED_AT, USER_ID, ACCESS_ID, "h", Permissions.none().with(scope, level), CREATED_AT, null))
                        .permissions();

                assertThat(published).as("%s %s", scope, level).hasSize(1);
                Map.Entry<ApiKeyScope, ApiKeyAccessLevel> entry = published.entrySet().iterator().next();
                assertThat(entry.getKey().name()).isEqualTo(scope.name());
                assertThat(entry.getValue().name()).isEqualTo(level.name());
            }
    }

    @Test
    void thePublishedNamesAreTheContract() {
        assertThat(java.util.Arrays.stream(ApiKeyScope.values()).map(Enum::name))
                .containsExactly("INGESTION", "TENANT", "CHANNEL");
        assertThat(java.util.Arrays.stream(ApiKeyAccessLevel.values()).map(Enum::name))
                .containsExactly("READ", "WRITE", "READ_WRITE");
    }
}
