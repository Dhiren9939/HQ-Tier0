package me.dhiren9939.backend.common.access.events.outbound;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The integration events are a published contract consumed by other contexts.
 * Changing a field name, order or event type string is a contract change and must fail here.
 */
class ApiKeyIntegrationEventContractTest {

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @Test
    void createdPayload() {
        assertThat(componentNames(ApiKeyCreatedIntegrationEvent.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "accessSecretHash", "permissions", "createdAt", "expiresAt");
        assertThat(ApiKeyCreatedIntegrationEvent.EVENT_TYPE).isEqualTo("apiKey.projection.create");
    }

    @Test
    void updatedPayload() {
        assertThat(componentNames(ApiKeyUpdatedIntegrationEvent.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "accessSecretHash", "permissions", "createdAt", "expiresAt");
        assertThat(ApiKeyUpdatedIntegrationEvent.EVENT_TYPE).isEqualTo("apiKey.projection.update");
    }

    @Test
    void deletedPayload() {
        assertThat(componentNames(ApiKeyDeletedIntegrationEvent.class)).containsExactly(
                "apiKeyName", "apiKeyId", "version", "occurredAt", "emittedAt", "userId", "accessId",
                "accessSecretHash", "permissions", "createdAt", "expiresAt");
        assertThat(ApiKeyDeletedIntegrationEvent.EVENT_TYPE).isEqualTo("apiKey.projection.delete");
    }

    @Test
    void everyCommandIsAFullSnapshotWithTheSameFields() {
        // A consumer that missed earlier events rebuilds the key from the latest one alone.
        List<String> create = componentNames(ApiKeyCreatedIntegrationEvent.class);
        assertThat(componentNames(ApiKeyUpdatedIntegrationEvent.class)).isEqualTo(create);
        assertThat(componentNames(ApiKeyDeletedIntegrationEvent.class)).isEqualTo(create);
        assertThat(Arrays.stream(ApiKeyIntegrationEvent.class.getMethods()).map(java.lang.reflect.Method::getName))
                .contains("accessSecretHash");
    }

    @Test
    void eventsCarryExpiryAndBothTimestamps() {
        for (Class<? extends Record> type : List.of(ApiKeyCreatedIntegrationEvent.class, ApiKeyUpdatedIntegrationEvent.class, ApiKeyDeletedIntegrationEvent.class)) {
            assertThat(componentNames(type)).contains("occurredAt", "emittedAt", "createdAt", "expiresAt");
        }
        assertThat(Arrays.stream(ApiKeyIntegrationEvent.class.getMethods()).map(java.lang.reflect.Method::getName))
                .contains("occurredAt", "emittedAt", "createdAt", "expiresAt");
    }

    @Test
    void permissionsPayloadIsAScopeToLevelMap() {
        // Scope names are the JSON keys; consumers read them as a plain string map.
        for (Class<? extends Record> type : List.of(ApiKeyCreatedIntegrationEvent.class, ApiKeyUpdatedIntegrationEvent.class, ApiKeyDeletedIntegrationEvent.class)) {
            assertThat(Arrays.stream(type.getRecordComponents())
                    .filter(component -> component.getName().equals("permissions"))
                    .map(RecordComponent::getType))
                    .containsExactly(java.util.Map.class);
        }
    }

    @Test
    void theEventInterfaceIsSealedAndPermitsExactlyTheThreeLifecycleEvents() {
        assertThat(ApiKeyIntegrationEvent.class.isSealed()).isTrue();
        assertThat(ApiKeyIntegrationEvent.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(ApiKeyCreatedIntegrationEvent.class, ApiKeyUpdatedIntegrationEvent.class, ApiKeyDeletedIntegrationEvent.class);
    }

    @Test
    void eventTypeMatchesConstant() {
        Instant at = Instant.parse("2026-01-15T11:00:00Z");
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertThat(ApiKeyCreatedIntegrationEvent.builder().apiKeyName("n").apiKeyId(id).version(1).occurredAt(at)
                .userId(id).accessId("a").accessSecretHash("h").permissions(Map.of()).createdAt(at).build().eventType())
                .isEqualTo("apiKey.projection.create");
        assertThat(ApiKeyUpdatedIntegrationEvent.builder().apiKeyName("n").apiKeyId(id).version(1).occurredAt(at)
                .userId(id).accessId("a").accessSecretHash("h").permissions(Map.of()).createdAt(at).build().eventType())
                .isEqualTo("apiKey.projection.update");
        assertThat(ApiKeyDeletedIntegrationEvent.builder().apiKeyName("n").apiKeyId(id).version(1).occurredAt(at)
                .userId(id).accessId("a").accessSecretHash("h").permissions(Map.of()).createdAt(at).build().eventType())
                .isEqualTo("apiKey.projection.delete");
    }
}
