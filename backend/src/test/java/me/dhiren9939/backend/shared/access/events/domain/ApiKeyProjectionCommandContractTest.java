package me.dhiren9939.backend.shared.access.events;

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
class ApiKeyEventContractTest {

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @Test
    void createdPayload() {
        assertThat(componentNames(ApiKeyCreated.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "accessSecretHash", "permissions", "createdAt");
        assertThat(ApiKeyCreated.EVENT_TYPE).isEqualTo("apiKey.created");
    }

    @Test
    void updatedPayload() {
        assertThat(componentNames(ApiKeyUpdated.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "permissions", "createdAt");
        assertThat(ApiKeyUpdated.EVENT_TYPE).isEqualTo("apiKey.updated");
    }

    @Test
    void deletedPayload() {
        assertThat(componentNames(ApiKeyDeleted.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "permissions", "createdAt");
        assertThat(ApiKeyDeleted.EVENT_TYPE).isEqualTo("apiKey.deleted");
    }

    @Test
    void eventsCarryNoExpiryAndBothTimestamps() {
        for (Class<? extends Record> type : List.of(ApiKeyCreated.class, ApiKeyUpdated.class, ApiKeyDeleted.class)) {
            assertThat(componentNames(type)).doesNotContain("expiresAt").contains("occurredAt", "emittedAt");
        }
        assertThat(Arrays.stream(ApiKeyEvent.class.getMethods()).map(java.lang.reflect.Method::getName))
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
        assertThat(ApiKeyEvent.class.isSealed()).isTrue();
        assertThat(ApiKeyEvent.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(ApiKeyCreated.class, ApiKeyUpdated.class, ApiKeyDeleted.class);
    }

    @Test
    void eventTypeMatchesConstant() {
        assertThat(ApiKeyCreated.builder().build().eventType()).isEqualTo("apiKey.created");
        assertThat(ApiKeyUpdated.builder().build().eventType()).isEqualTo("apiKey.updated");
        assertThat(ApiKeyDeleted.builder().build().eventType()).isEqualTo("apiKey.deleted");
    }
}
