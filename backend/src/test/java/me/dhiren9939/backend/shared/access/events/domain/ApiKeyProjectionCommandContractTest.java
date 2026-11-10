package me.dhiren9939.backend.shared.access.events.domain;

import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.Scope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lifecycle events are a published contract consumed by other contexts.
 * Changing a field name, order or event type string is a contract change and must fail here.
 */
class ApiKeyProjectionCommandContractTest {

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @Test
    void createdPayload() {
        assertThat(componentNames(CreateApiKeyProjectionCommand.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "accessSecretHash", "permissions", "createdAt");
        assertThat(CreateApiKeyProjectionCommand.EVENT_TYPE).isEqualTo("apiKey.projection.create");
    }

    @Test
    void updatedPayload() {
        assertThat(componentNames(UpdateApiKeyProjectionCommand.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "permissions", "createdAt");
        assertThat(UpdateApiKeyProjectionCommand.EVENT_TYPE).isEqualTo("apiKey.projection.update");
    }

    @Test
    void deletedPayload() {
        assertThat(componentNames(DeleteApiKeyProjectionCommand.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "permissions", "createdAt");
        assertThat(DeleteApiKeyProjectionCommand.EVENT_TYPE).isEqualTo("apiKey.projection.delete");
    }

    @Test
    void eventsCarryNoExpiryAndBothTimestamps() {
        for (Class<? extends Record> type : List.of(CreateApiKeyProjectionCommand.class, UpdateApiKeyProjectionCommand.class, DeleteApiKeyProjectionCommand.class)) {
            assertThat(componentNames(type)).doesNotContain("expiresAt").contains("occurredAt", "emittedAt");
        }
        assertThat(Arrays.stream(ApiKeyProjectionCommand.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("expiresAt").contains("occurredAt", "emittedAt");
    }

    @Test
    void permissionsPayloadIsScopeToLevelAndItsNamesAreWireValues() {
        // Scope and level names are the JSON keys/values consumers read; renaming one is a contract change.
        assertThat(Arrays.stream(Scope.values()).map(Enum::name)).containsExactly("INGESTION", "TENANT", "CHANNEL");
        assertThat(Arrays.stream(AccessLevel.values()).map(Enum::name))
                .containsExactly("NONE", "READ", "WRITE", "READ_WRITE");
        assertThat(Permissions.class.isRecord()).isFalse();
        assertThat(Permissions.none().with(Scope.TENANT, AccessLevel.READ).asMap())
                .containsExactly(java.util.Map.entry(Scope.TENANT, AccessLevel.READ));
    }

    @Test
    void theEventInterfaceIsSealedAndPermitsExactlyTheThreeLifecycleEvents() {
        assertThat(ApiKeyProjectionCommand.class.isSealed()).isTrue();
        assertThat(ApiKeyProjectionCommand.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(CreateApiKeyProjectionCommand.class, UpdateApiKeyProjectionCommand.class, DeleteApiKeyProjectionCommand.class);
    }

    @Test
    void eventTypeMatchesConstant() {
        assertThat(CreateApiKeyProjectionCommand.builder().build().eventType()).isEqualTo("apiKey.projection.create");
        assertThat(UpdateApiKeyProjectionCommand.builder().build().eventType()).isEqualTo("apiKey.projection.update");
        assertThat(DeleteApiKeyProjectionCommand.builder().build().eventType()).isEqualTo("apiKey.projection.delete");
    }
}
