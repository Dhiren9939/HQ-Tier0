package me.dhiren9939.backend.access.infrastructure.persistence.models;

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

/** Pins down how integration events map to outbox rows: metadata in columns, the rest in the payload. */
class EventOutboxEntityTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID API_KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Map<ApiKeyScope, ApiKeyAccessLevel> PERMISSIONS =
            Map.of(ApiKeyScope.INGESTION, ApiKeyAccessLevel.WRITE);
    private static final String PAYLOAD = "{\"already\":\"serialized\"}";

    @Test
    void createdEventBecomesARowWithItsMetadataAndPayload() {
        EventOutboxEntity row = EventOutboxEntity.of(EVENT_ID, ApiKeyCreatedIntegrationEvent.builder()
                .apiKeyName("test-key").apiKeyId(API_KEY_ID).version(1).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc").accessSecretHash("hashed_secret")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build(), PAYLOAD);

        assertThat(row.getEventId()).isEqualTo(EVENT_ID);
        assertThat(row.getEventType()).isEqualTo("apiKey.projection.create");
        assertThat(row.getApiKeyId()).isEqualTo(API_KEY_ID);
        assertThat(row.getVersion()).isEqualTo(1);
        assertThat(row.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(row.getEmittedAt()).isNull();
        assertThat(row.getPayload()).as("stored as given, never rebuilt").isEqualTo(PAYLOAD);
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void updatedEventBecomesARowWithItsMetadata() {
        EventOutboxEntity row = EventOutboxEntity.of(EVENT_ID, ApiKeyUpdatedIntegrationEvent.builder()
                .apiKeyName("renamed").apiKeyId(API_KEY_ID).version(2).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc").accessSecretHash("hashed_secret")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build(), PAYLOAD);

        assertThat(row.getEventType()).isEqualTo("apiKey.projection.update");
        assertThat(row.getVersion()).isEqualTo(2);
    }

    @Test
    void deletedEventBecomesARowWithItsMetadata() {
        EventOutboxEntity row = EventOutboxEntity.of(EVENT_ID, ApiKeyDeletedIntegrationEvent.builder()
                .apiKeyName("test-key").apiKeyId(API_KEY_ID).version(3).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc").accessSecretHash("hashed_secret")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build(), PAYLOAD);

        assertThat(row.getEventType()).isEqualTo("apiKey.projection.delete");
        assertThat(row.getVersion()).isEqualTo(3);
    }

    @Test
    void emittedSetsTheColumnAndReplacesThePayloadWithWhatWasSent() {
        EventOutboxEntity row = EventOutboxEntity.builder().eventId(EVENT_ID).payload(PAYLOAD).build();
        Instant at = Instant.parse("2026-01-15T12:00:05Z");

        row.emitted(at, "{\"sent\":true}");

        assertThat(row.getEmittedAt()).isEqualTo(at);
        assertThat(row.getPayload()).isEqualTo("{\"sent\":true}");
    }

    @Test
    void aDeadLetterKeepsTheRowsMetadataPayloadAndFailureHistory() {
        EventOutboxEntity row = EventOutboxEntity.builder().eventId(EVENT_ID).eventType("apiKey.projection.update")
                .apiKeyId(API_KEY_ID).version(4).occurredAt(OCCURRED_AT).payload(PAYLOAD)
                .attempts(10).lastError("refused").build();
        Instant deadAt = Instant.parse("2026-01-15T13:00:00Z");

        DeadLetterEntity dead = DeadLetterEntity.of(row, deadAt);

        assertThat(dead.getEventId()).isEqualTo(EVENT_ID);
        assertThat(dead.getEventType()).isEqualTo("apiKey.projection.update");
        assertThat(dead.getApiKeyId()).isEqualTo(API_KEY_ID);
        assertThat(dead.getVersion()).isEqualTo(4);
        assertThat(dead.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(dead.getPayload()).isEqualTo(PAYLOAD);
        assertThat(dead.getAttempts()).isEqualTo(10);
        assertThat(dead.getLastError()).isEqualTo("refused");
        assertThat(dead.getDeadAt()).isEqualTo(deadAt);
    }
}
