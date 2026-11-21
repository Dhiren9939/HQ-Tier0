package me.dhiren9939.backend.access.infrastructure.serialization;

import me.dhiren9939.backend.common.access.events.outbound.ApiKeyAccessLevel;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyScope;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The published contract, pinned by the golden files in {@code src/test/resources/contracts/apikey.projection}. The
 * serializer must write exactly those bodies, accept them and refuse any change to their key set. Consumers test
 * their readers against the same files, so a field added on one side without the other fails a test, not production.
 */
class ApiKeyProjectionGoldenContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String GOLDEN = "/contracts/apikey.projection/";

    private final IntegrationEventSerializer serializer = new IntegrationEventSerializer(JSON);

    private static final UUID API_KEY_ID = UUID.fromString("01968a3e-0000-7000-8000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACCESS_ID = "V1_0PxK-z_9AbCdEfGhIjKlMn";
    private static final String HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:00:00Z");

    /** The events the golden files describe, built in code. */
    private static final Map<String, Golden> EVENTS = Map.of(
            "create", new Golden(UUID.fromString("00000000-0000-7000-8000-0000000000c1"),
                    ApiKeyCreatedIntegrationEvent.builder().apiKeyName("orders").apiKeyId(API_KEY_ID).version(1)
                            .occurredAt(CREATED_AT).userId(USER_ID).accessId(ACCESS_ID).accessSecretHash(HASH)
                            .permissions(permissions(Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE,
                                    ApiKeyScope.TENANT, ApiKeyAccessLevel.READ_WRITE,
                                    ApiKeyScope.CHANNEL, ApiKeyAccessLevel.READ)))
                            .createdAt(CREATED_AT).expiresAt(Instant.parse("2026-02-15T11:00:00Z")).build()),
            "update", new Golden(UUID.fromString("00000000-0000-7000-8000-0000000000c2"),
                    ApiKeyUpdatedIntegrationEvent.builder().apiKeyName("orders-v2").apiKeyId(API_KEY_ID).version(2)
                            .occurredAt(Instant.parse("2026-01-16T09:30:00.123456Z")).userId(USER_ID)
                            .accessId(ACCESS_ID).accessSecretHash(HASH)
                            .permissions(permissions(Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE)))
                            .createdAt(CREATED_AT).expiresAt(null).build()),
            "delete", new Golden(UUID.fromString("00000000-0000-7000-8000-0000000000c3"),
                    ApiKeyDeletedIntegrationEvent.builder().apiKeyName("orders-v2").apiKeyId(API_KEY_ID).version(3)
                            .occurredAt(Instant.parse("2026-01-17T08:00:00Z")).userId(USER_ID)
                            .accessId(ACCESS_ID).accessSecretHash(HASH)
                            .permissions(permissions(Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE)))
                            .createdAt(CREATED_AT).expiresAt(null).build()));

    private record Golden(UUID eventId, ApiKeyIntegrationEvent event) {
    }

    private static Map<ApiKeyScope, ApiKeyAccessLevel> permissions(Map<ApiKeyScope, ApiKeyAccessLevel> levels) {
        return new EnumMap<>(levels);
    }

    private static String golden(String name) {
        try (InputStream in = ApiKeyProjectionGoldenContractTest.class.getResourceAsStream(GOLDEN + name)) {
            assertThat(in).as("golden file " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode tree(String json) {
        return JSON.readTree(json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "delete"})
    void theSerializerWritesExactlyTheGoldenBody(String type) {
        Golden golden = EVENTS.get(type);

        assertThat(tree(serializer.toJson(golden.eventId(), golden.event()))).isEqualTo(tree(golden(type + ".json")));
    }

    @Test
    void publishingAddsOnlyEmittedAtToTheGoldenBody() {
        String published = serializer.withEmittedAt(golden("create.json"), Instant.parse("2026-01-15T11:00:02.500Z"));

        assertThat(tree(published)).isEqualTo(tree(golden("create.published.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "delete"})
    void everyGoldenHasExactlyTheStoredKeys(String type) {
        Set<String> keys = new HashSet<>(tree(golden(type + ".json")).propertyNames());

        assertThat(keys).isEqualTo(IntegrationEventSerializer.STORED_KEYS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "delete"})
    void removingAnyKeyOrAddingOneIsRefused(String type) {
        ObjectNode golden = (ObjectNode) tree(golden(type + ".json"));
        Instant at = Instant.parse("2026-01-17T08:00:01Z");

        for (String key : golden.propertyNames()) {
            ObjectNode without = golden.deepCopy();
            without.remove(key);
            assertThatThrownBy(() -> serializer.withEmittedAt(without.toString(), at))
                    .as("without " + key).isInstanceOf(IntegrationEventSerializationException.class)
                    .hasMessageContaining(key);
        }
        ObjectNode extra = golden.deepCopy();
        extra.put("newField", "x");
        assertThatThrownBy(() -> serializer.withEmittedAt(extra.toString(), at))
                .isInstanceOf(IntegrationEventSerializationException.class).hasMessageContaining("newField");
    }

    @Test
    void theGoldensUseEveryScopeAndLevelByItsWireName() {
        Set<String> scopes = new HashSet<>();
        Set<String> levels = new HashSet<>();
        for (String type : new String[]{"create", "update", "delete"})
            tree(golden(type + ".json")).get("permissions").properties().forEach(grant -> {
                scopes.add(grant.getKey());
                levels.add(grant.getValue().asString());
            });

        assertThat(scopes).as("add a new scope to a golden file").containsExactlyInAnyOrderElementsOf(
                Arrays.stream(ApiKeyScope.values()).map(ApiKeyScope::wireName).toList());
        assertThat(levels).as("add a new level to a golden file").containsExactlyInAnyOrderElementsOf(
                Arrays.stream(ApiKeyAccessLevel.values()).map(ApiKeyAccessLevel::wireName).toList());
    }

    @Test
    void wireNamesAreUniqueAndReadBack() {
        for (ApiKeyScope scope : ApiKeyScope.values())
            assertThat(ApiKeyScope.fromWire(scope.wireName())).contains(scope);
        for (ApiKeyAccessLevel level : ApiKeyAccessLevel.values())
            assertThat(ApiKeyAccessLevel.fromWire(level.wireName())).contains(level);
        assertThat(ApiKeyScope.fromWire("ingestion")).isEmpty();
        assertThat(ApiKeyAccessLevel.fromWire("NONE")).isEmpty();
    }
}
