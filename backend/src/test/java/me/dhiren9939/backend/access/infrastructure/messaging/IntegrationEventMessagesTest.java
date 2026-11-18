package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.codec.WrapperMessageBuilder;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The message body and headers are the wire contract consumers read from the stream. */
class IntegrationEventMessagesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant EMITTED_AT = Instant.parse("2026-01-15T12:00:05Z");

    private final IntegrationEventMessages messages = new IntegrationEventMessages(JSON);

    static EventOutboxEntity row() {
        return EventOutboxEntity.builder()
                .eventId(UUID.fromString("0190a6e0-0000-7000-8000-000000000001"))
                .eventType(ApiKeyUpdatedIntegrationEvent.EVENT_TYPE)
                .apiKeyId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .version(2)
                .occurredAt(Instant.parse("2026-01-15T12:00:00Z"))
                .userId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .accessId("V1_acc")
                .accessSecretHash("hashed_secret")
                .apiKeyName("renamed")
                .permissions("{\"INGESTION\":\"WRITE\",\"TENANT\":\"READ\"}")
                .createdAt(Instant.parse("2026-01-15T11:00:00Z"))
                .build();
    }

    @Test
    void theBodyHasTheEventTypeAndExactlyTheIntegrationEventFieldsInOrder() {
        JsonNode body = JSON.readTree(messages.body(row(), EMITTED_AT));

        List<String> expected = new ArrayList<>(List.of("eventType"));
        Arrays.stream(ApiKeyCreatedIntegrationEvent.class.getRecordComponents()).map(RecordComponent::getName)
                .forEach(expected::add);
        assertThat(body.propertyNames()).containsExactlyElementsOf(expected);
        for (Class<? extends Record> type : List.of(ApiKeyUpdatedIntegrationEvent.class, ApiKeyDeletedIntegrationEvent.class)) {
            assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName))
                    .containsExactlyElementsOf(expected.subList(1, expected.size()));
        }
    }

    @Test
    void theBodyCarriesTheRowAsStoredWithTheEmissionTime() {
        JsonNode body = JSON.readTree(messages.body(row(), EMITTED_AT));

        assertThat(body.get("eventType").asString()).isEqualTo("apiKey.projection.update");
        assertThat(body.get("apiKeyId").asString()).isEqualTo("00000000-0000-0000-0000-000000000002");
        assertThat(body.get("version").asLong()).isEqualTo(2);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-01-15T12:00:00Z");
        assertThat(body.get("emittedAt").asString()).isEqualTo("2026-01-15T12:00:05Z");
        assertThat(body.get("accessSecretHash").asString()).isEqualTo("hashed_secret");
        assertThat(body.get("permissions").isObject()).isTrue();
        assertThat(body.get("permissions").get("TENANT").asString()).isEqualTo("READ");
        assertThat(body.get("expiresAt").isNull()).as("never expires").isTrue();
    }

    @Test
    void headersIdentifyTheEventAndCarryTheRoutingKey() {
        EventOutboxEntity row = row();

        Message message = messages.toMessage(row, EMITTED_AT, new WrapperMessageBuilder());

        assertThat(message.getProperties().getMessageIdAsUuid()).isEqualTo(row.getEventId());
        assertThat(message.getProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getApplicationProperties())
                .containsEntry("eventType", "apiKey.projection.update")
                .containsEntry("apiKeyId", "00000000-0000-0000-0000-000000000002")
                .containsEntry("version", 2L);
        assertThat(JSON.readTree(message.getBodyAsBinary()).get("apiKeyName").asString()).isEqualTo("renamed");
    }

    @Test
    void permissionsThatAreNotAJsonObjectMakeTheRowUnpublishable() {
        EventOutboxEntity notJson = row();
        notJson.setPermissions("not json");
        EventOutboxEntity notAnObject = row();
        notAnObject.setPermissions("[\"INGESTION\"]");

        assertThatThrownBy(() -> messages.body(notJson, EMITTED_AT)).isInstanceOf(UnpublishableEventException.class);
        assertThatThrownBy(() -> messages.body(notAnObject, EMITTED_AT)).isInstanceOf(UnpublishableEventException.class);
    }
}
