package me.dhiren9939.backend.common.access.events.outbound;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection commands are a published contract consumed by other contexts.
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
                "accessSecretHash", "permissions", "createdAt", "expiresAt");
        assertThat(CreateApiKeyProjectionCommand.EVENT_TYPE).isEqualTo("apiKey.projection.create");
    }

    @Test
    void updatedPayload() {
        assertThat(componentNames(UpdateApiKeyProjectionCommand.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "permissions", "createdAt", "expiresAt");
        assertThat(UpdateApiKeyProjectionCommand.EVENT_TYPE).isEqualTo("apiKey.projection.update");
    }

    @Test
    void deletedPayload() {
        assertThat(componentNames(DeleteApiKeyProjectionCommand.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "permissions", "createdAt", "expiresAt");
        assertThat(DeleteApiKeyProjectionCommand.EVENT_TYPE).isEqualTo("apiKey.projection.delete");
    }

    @Test
    void eventsCarryExpiryAndBothTimestamps() {
        for (Class<? extends Record> type : List.of(CreateApiKeyProjectionCommand.class, UpdateApiKeyProjectionCommand.class, DeleteApiKeyProjectionCommand.class)) {
            assertThat(componentNames(type)).contains("occurredAt", "emittedAt", "createdAt", "expiresAt");
        }
        assertThat(Arrays.stream(ApiKeyProjectionCommand.class.getMethods()).map(java.lang.reflect.Method::getName))
                .contains("occurredAt", "emittedAt", "createdAt", "expiresAt");
    }

    @Test
    void permissionsPayloadIsAScopeToLevelMap() {
        // Scope names are the JSON keys; consumers read them as a plain string map.
        for (Class<? extends Record> type : List.of(CreateApiKeyProjectionCommand.class, UpdateApiKeyProjectionCommand.class, DeleteApiKeyProjectionCommand.class)) {
            assertThat(Arrays.stream(type.getRecordComponents())
                    .filter(component -> component.getName().equals("permissions"))
                    .map(RecordComponent::getType))
                    .containsExactly(java.util.Map.class);
        }
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
