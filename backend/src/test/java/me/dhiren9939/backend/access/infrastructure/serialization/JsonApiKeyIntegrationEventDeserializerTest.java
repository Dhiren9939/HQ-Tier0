package me.dhiren9939.backend.access.infrastructure.serialization;

import me.dhiren9939.backend.common.access.events.outbound.ApiKeyAccessLevel;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyEventDeserializationException;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyScope;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reads published apiKey.projection bodies back into their events, as strictly as the serializer writes them. */
class JsonApiKeyIntegrationEventDeserializerTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String GOLDEN = "/contracts/apikey.projection/";
    private static final Instant EMITTED_AT = Instant.parse("2026-01-15T11:00:02.500Z");
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:00:00Z");

    private final IntegrationEventSerializer serializer = new IntegrationEventSerializer(JSON);
    private final JsonApiKeyIntegrationEventDeserializer deserializer =
            new JsonApiKeyIntegrationEventDeserializer(JSON, serializer);

    private static String golden(String name) {
        try (InputStream in = JsonApiKeyIntegrationEventDeserializerTest.class.getResourceAsStream(GOLDEN + name)) {
            assertThat(in).as("golden file " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ObjectNode published() {
        return (ObjectNode) JSON.readTree(golden("create.published.json"));
    }

    private static ApiKeyIntegrationEvent withEmittedAt(ApiKeyIntegrationEvent event) {
        return switch (event) {
            case ApiKeyCreatedIntegrationEvent e -> e.toBuilder().emittedAt(EMITTED_AT).build();
            case ApiKeyUpdatedIntegrationEvent e -> e.toBuilder().emittedAt(EMITTED_AT).build();
            case ApiKeyDeletedIntegrationEvent e -> e.toBuilder().emittedAt(EMITTED_AT).build();
        };
    }

    @Test
    void readsThePublishedGolden() {
        ApiKeyIntegrationEvent event = deserializer.deserialize(golden("create.published.json"));

        assertThat(event).isInstanceOf(ApiKeyCreatedIntegrationEvent.class);
        assertThat(event.emittedAt()).isEqualTo(EMITTED_AT);
        assertThat(event.apiKeyName()).isEqualTo("orders");
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.permissions())
                .containsEntry(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE)
                .containsEntry(ApiKeyScope.TENANT, ApiKeyAccessLevel.READ_WRITE)
                .containsEntry(ApiKeyScope.CHANNEL, ApiKeyAccessLevel.READ);
    }

    @Test
    void roundTripsWhatTheSerializerPublishes() {
        List<ApiKeyIntegrationEvent> events = List.of(
                ApiKeyCreatedIntegrationEvent.builder().apiKeyName("orders").apiKeyId(UUID.randomUUID()).version(1)
                        .occurredAt(CREATED_AT).userId(UUID.randomUUID()).accessId("id").accessSecretHash("hash")
                        .permissions(Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE))
                        .createdAt(CREATED_AT).expiresAt(null).build(),
                ApiKeyUpdatedIntegrationEvent.builder().apiKeyName("orders").apiKeyId(UUID.randomUUID()).version(2)
                        .occurredAt(Instant.parse("2026-01-16T11:00:00.123456Z")).userId(UUID.randomUUID())
                        .accessId("id").accessSecretHash("hash")
                        .permissions(Map.of(ApiKeyScope.CHANNEL, ApiKeyAccessLevel.READ))
                        .createdAt(CREATED_AT).expiresAt(Instant.parse("2026-03-01T00:00:00Z")).build(),
                ApiKeyDeletedIntegrationEvent.builder().apiKeyName("orders").apiKeyId(UUID.randomUUID()).version(3)
                        .occurredAt(Instant.parse("2026-01-17T11:00:00Z")).userId(UUID.randomUUID())
                        .accessId("id").accessSecretHash("hash").permissions(Map.of())
                        .createdAt(CREATED_AT).expiresAt(null).build());

        for (ApiKeyIntegrationEvent original : events) {
            String body = serializer.withEmittedAt(serializer.toJson(UUID.randomUUID(), original), EMITTED_AT);

            assertThat(deserializer.deserialize(body)).as(original.eventType()).isEqualTo(withEmittedAt(original));
        }
    }

    @Test
    void refusesAStoredBodyWithoutEmittedAt() {
        assertThatThrownBy(() -> deserializer.deserialize(golden("create.json")))
                .isInstanceOf(ApiKeyEventDeserializationException.class).hasMessageContaining("emittedAt");
    }

    @Test
    void refusesAnExtraOrMissingKey() {
        ObjectNode extra = published();
        extra.put("newField", "x");
        ObjectNode missing = published();
        missing.remove("accessId");

        assertThatThrownBy(() -> deserializer.deserialize(extra.toString()))
                .isInstanceOf(ApiKeyEventDeserializationException.class).hasMessageContaining("newField");
        assertThatThrownBy(() -> deserializer.deserialize(missing.toString()))
                .isInstanceOf(ApiKeyEventDeserializationException.class).hasMessageContaining("accessId");
    }

    @Test
    void refusesAnUnknownEventType() {
        ObjectNode body = published();
        body.put("eventType", "apiKey.projection.rotate");

        assertThatThrownBy(() -> deserializer.deserialize(body.toString()))
                .isInstanceOf(ApiKeyEventDeserializationException.class).hasMessageContaining("eventType");
    }

    @Test
    void refusesANonCanonicalEmittedAtOrEventId() {
        ObjectNode badTime = published();
        badTime.put("emittedAt", "2026-01-15T11:00:02.500+00:00");
        ObjectNode badId = published();
        badId.put("eventId", "00000000-0000-7000-8000-0000000000C1");

        assertThatThrownBy(() -> deserializer.deserialize(badTime.toString()))
                .isInstanceOf(ApiKeyEventDeserializationException.class).hasMessageContaining("emittedAt");
        assertThatThrownBy(() -> deserializer.deserialize(badId.toString()))
                .isInstanceOf(ApiKeyEventDeserializationException.class).hasMessageContaining("eventId");
    }

    @Test
    void refusesAnUnknownScopeAndIngestionWithoutWrite() {
        ObjectNode unknownScope = published();
        ((ObjectNode) unknownScope.get("permissions")).put("ADMIN", "WRITE");
        ObjectNode ingestionRead = published();
        ((ObjectNode) ingestionRead.get("permissions")).put("INGESTION", "READ");

        assertThatThrownBy(() -> deserializer.deserialize(unknownScope.toString()))
                .isInstanceOf(ApiKeyEventDeserializationException.class).hasMessageContaining("ADMIN");
        assertThatThrownBy(() -> deserializer.deserialize(ingestionRead.toString()))
                .isInstanceOf(ApiKeyEventDeserializationException.class).hasMessageContaining("INGESTION");
    }

    @Test
    void refusesNullNonJsonAndNonObjects() {
        for (String body : new String[]{null, "nope", "[]", "\"text\""})
            assertThatThrownBy(() -> deserializer.deserialize(body)).as(String.valueOf(body))
                    .isInstanceOf(ApiKeyEventDeserializationException.class);
    }
}
