package me.dhiren9939.backend.common.access.events.outbound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** An integration event that breaks the published contract cannot be created; the message names the field. */
class IntegrationEventRulesTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:00:00Z");

    private static ApiKeyUpdatedIntegrationEvent.ApiKeyUpdatedIntegrationEventBuilder valid() {
        return ApiKeyUpdatedIntegrationEvent.builder()
                .apiKeyName("orders")
                .apiKeyId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .version(1)
                .occurredAt(CREATED_AT)
                .userId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .accessId("V1_acc")
                .accessSecretHash("hash")
                .permissions(Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE))
                .createdAt(CREATED_AT)
                .expiresAt(CREATED_AT.plusSeconds(60));
    }

    private static Arguments rejected(String field, UnaryOperator<ApiKeyUpdatedIntegrationEvent.ApiKeyUpdatedIntegrationEventBuilder> change) {
        return Arguments.of(field, change);
    }

    static Stream<Arguments> brokenEvents() {
        Map<ApiKeyScope, ApiKeyAccessLevel> noLevel = new HashMap<>();
        noLevel.put(ApiKeyScope.TENANT, null);
        Map<ApiKeyScope, ApiKeyAccessLevel> noScope = new HashMap<>();
        noScope.put(null, ApiKeyAccessLevel.READ);
        return Stream.of(
                rejected("apiKeyName", b -> b.apiKeyName(null)),
                rejected("apiKeyName", b -> b.apiKeyName("")),
                rejected("apiKeyName", b -> b.apiKeyName("   ")),
                rejected("apiKeyName", b -> b.apiKeyName("x".repeat(257))),
                rejected("apiKeyId", b -> b.apiKeyId(null)),
                rejected("version", b -> b.version(0)),
                rejected("version", b -> b.version(-1)),
                rejected("occurredAt", b -> b.occurredAt(null)),
                rejected("occurredAt", b -> b.occurredAt(CREATED_AT.minusSeconds(1))),
                rejected("userId", b -> b.userId(null)),
                rejected("accessId", b -> b.accessId(null)),
                rejected("accessId", b -> b.accessId(" ")),
                rejected("accessId", b -> b.accessId("x".repeat(257))),
                rejected("accessSecretHash", b -> b.accessSecretHash(null)),
                rejected("accessSecretHash", b -> b.accessSecretHash("")),
                rejected("accessSecretHash", b -> b.accessSecretHash("x".repeat(257))),
                rejected("createdAt", b -> b.createdAt(null)),
                rejected("expiresAt", b -> b.expiresAt(CREATED_AT)),
                rejected("expiresAt", b -> b.expiresAt(CREATED_AT.minusSeconds(1))),
                rejected("permissions", b -> b.permissions(null)),
                rejected("permissions", b -> b.permissions(noLevel)),
                rejected("permissions", b -> b.permissions(noScope)),
                rejected("permissions", b -> b.permissions(Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.READ))),
                rejected("permissions", b -> b.permissions(Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.READ_WRITE))));
    }

    @ParameterizedTest(name = "{0} rejected")
    @MethodSource("brokenEvents")
    void anEventThatBreaksTheContractCannotBeCreated(String field,
                                                     UnaryOperator<ApiKeyUpdatedIntegrationEvent.ApiKeyUpdatedIntegrationEventBuilder> change) {
        assertThatThrownBy(() -> change.apply(valid()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(field)
                .hasMessageContaining("apiKey.projection.update");
    }

    @Test
    void everyEventTypeAppliesTheSameRules() {
        ApiKeyUpdatedIntegrationEvent base = valid().build();

        assertThatThrownBy(() -> ApiKeyCreatedIntegrationEvent.builder().apiKeyName(base.apiKeyName())
                .apiKeyId(base.apiKeyId()).version(0).occurredAt(CREATED_AT).userId(base.userId())
                .accessId("a").accessSecretHash("h").permissions(Map.of()).createdAt(CREATED_AT).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("apiKey.projection.create");
        assertThatThrownBy(() -> ApiKeyDeletedIntegrationEvent.builder().apiKeyName(base.apiKeyName())
                .apiKeyId(base.apiKeyId()).version(0).occurredAt(CREATED_AT).userId(base.userId())
                .accessId("a").accessSecretHash("h").permissions(Map.of()).createdAt(CREATED_AT).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("apiKey.projection.delete");
    }

    @Test
    void theEdgesOfTheRulesAreAllowed() {
        ApiKeyUpdatedIntegrationEvent event = valid()
                .apiKeyName("x".repeat(256)).accessId("x".repeat(256)).accessSecretHash("x".repeat(256))
                .version(Long.MAX_VALUE).expiresAt(null).permissions(Map.of()).build();

        assertThat(event.expiresAt()).as("null means the key never expires").isNull();
        assertThat(event.permissions()).as("no access is an empty map").isEmpty();
        assertThat(valid().expiresAt(CREATED_AT.plusNanos(1)).build()).isNotNull();
        assertThat(valid().occurredAt(CREATED_AT.plusSeconds(5)).build()).as("occurredAt may be after createdAt").isNotNull();
    }

    @Test
    void permissionsAreAnUnmodifiableCopyOrderedByScope() {
        Map<ApiKeyScope, ApiKeyAccessLevel> given = new HashMap<>();
        given.put(ApiKeyScope.CHANNEL, ApiKeyAccessLevel.READ);
        given.put(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE);
        ApiKeyUpdatedIntegrationEvent event = valid().permissions(given).build();

        given.put(ApiKeyScope.TENANT, ApiKeyAccessLevel.READ_WRITE);

        assertThat(event.permissions().keySet()).containsExactly(ApiKeyScope.INGESTION, ApiKeyScope.CHANNEL);
        assertThatThrownBy(() -> event.permissions().put(ApiKeyScope.TENANT, ApiKeyAccessLevel.READ))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emittedAtIsNotChecked() {
        // It is stamped when the event is published and is not part of what the outbox stores.
        assertThat(valid().emittedAt(CREATED_AT).build().emittedAt()).isEqualTo(CREATED_AT);
    }
}
