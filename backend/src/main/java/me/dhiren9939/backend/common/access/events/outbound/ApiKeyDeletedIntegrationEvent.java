package me.dhiren9939.backend.common.access.events.outbound;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when an API key is deleted. Carries the key's full state as it was just before deletion.
 */
@Builder(toBuilder = true)
public record ApiKeyDeletedIntegrationEvent(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt, Instant emittedAt,
                            UUID userId, String accessId,
                            String accessSecretHash,
                            Map<ApiKeyScope, ApiKeyAccessLevel> permissions,
                            Instant createdAt, Instant expiresAt) implements ApiKeyIntegrationEvent {

    public static final String EVENT_TYPE = "apiKey.projection.delete";

    /** Refuses, naming the field, anything the published contract does not allow. */
    public ApiKeyDeletedIntegrationEvent {
        permissions = IntegrationEventRules.check(EVENT_TYPE, apiKeyName, apiKeyId, version, occurredAt, userId, accessId,
                accessSecretHash, permissions, createdAt, expiresAt);
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
