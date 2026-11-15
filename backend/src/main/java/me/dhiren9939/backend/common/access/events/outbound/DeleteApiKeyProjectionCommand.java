package me.dhiren9939.backend.common.access.events.outbound;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when an API key is deleted.
 */
@Builder
public record DeleteApiKeyProjectionCommand(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt, Instant emittedAt,
                            UUID userId, String accessId,
                            Map<String, String> permissions,
                            Instant createdAt, Instant expiresAt) implements ApiKeyProjectionCommand {

    public static final String EVENT_TYPE = "apiKey.projection.delete";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
