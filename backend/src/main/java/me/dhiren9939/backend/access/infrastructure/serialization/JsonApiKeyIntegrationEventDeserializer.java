package me.dhiren9939.backend.access.infrastructure.serialization;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyEventDeserializationException;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEventDeserializer;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Optional;

/**
 * Reads a published body: the stored body plus {@code emittedAt}. The stored part is checked by
 * {@link IntegrationEventSerializer#validate}, so the keys, JSON forms and value rules are the ones the writer
 * enforces; this class only adds {@code emittedAt}, which a stored body must not have and a published one must.
 */
@Component
@RequiredArgsConstructor
public class JsonApiKeyIntegrationEventDeserializer implements ApiKeyIntegrationEventDeserializer {
    private final JsonMapper json;
    private final IntegrationEventSerializer serializer;

    @Override
    public ApiKeyIntegrationEvent deserialize(String body) {
        try {
            if (body == null || !(json.readTree(body) instanceof ObjectNode stored))
                throw new ApiKeyEventDeserializationException("The published body is not a JSON object");
            JsonNode emittedAtNode = stored.remove(IntegrationEventSerializer.EMITTED_AT);
            if (emittedAtNode == null)
                throw new ApiKeyEventDeserializationException(
                        "Integration event field " + IntegrationEventSerializer.EMITTED_AT + " is missing");
            IntegrationEventSerializer.instant(IntegrationEventSerializer.EMITTED_AT, emittedAtNode);
            Instant emittedAt = Instant.parse(emittedAtNode.asString());
            return withEmittedAt(serializer.validate(stored, Optional.empty()), emittedAt);
        } catch (IntegrationEventSerializationException e) {
            throw new ApiKeyEventDeserializationException(e.getMessage(), e);
        } catch (JacksonException e) {
            throw new ApiKeyEventDeserializationException("The published body is not valid JSON", e);
        } catch (IllegalArgumentException e) {
            throw new ApiKeyEventDeserializationException(e.getMessage(), e);
        }
    }

    private static ApiKeyIntegrationEvent withEmittedAt(ApiKeyIntegrationEvent event, Instant emittedAt) {
        return switch (event) {
            case ApiKeyCreatedIntegrationEvent created -> created.toBuilder().emittedAt(emittedAt).build();
            case ApiKeyUpdatedIntegrationEvent updated -> updated.toBuilder().emittedAt(emittedAt).build();
            case ApiKeyDeletedIntegrationEvent deleted -> deleted.toBuilder().emittedAt(emittedAt).build();
        };
    }
}
