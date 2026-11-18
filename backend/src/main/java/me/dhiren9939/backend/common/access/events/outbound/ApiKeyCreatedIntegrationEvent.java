package me.dhiren9939.backend.common.access.events.outbound;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when an API key is created. Carries the key's full state, like every integration event.
 */
@Builder(toBuilder = true)
public record ApiKeyCreatedIntegrationEvent(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt, Instant emittedAt,
                            UUID userId, String accessId,
                            String accessSecretHash,
                            Map<String, String> permissions,
                            Instant createdAt, Instant expiresAt) implements ApiKeyIntegrationEvent {

    public static final String EVENT_TYPE = "apiKey.projection.create";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
