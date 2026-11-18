package me.dhiren9939.backend.common.access.events.outbound;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when an API key name, permissions, or expiration is updated. Carries the key's full state.
 */
@Builder(toBuilder = true)
public record ApiKeyUpdatedIntegrationEvent(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt, Instant emittedAt,
                            UUID userId, String accessId,
                            String accessSecretHash,
                            Map<String, String> permissions,
                            Instant createdAt, Instant expiresAt) implements ApiKeyIntegrationEvent {

    public static final String EVENT_TYPE = "apiKey.projection.update";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
