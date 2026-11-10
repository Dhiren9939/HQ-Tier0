package me.dhiren9939.backend.shared.access.events.domain;

import lombok.Builder;
import me.dhiren9939.backend.shared.access.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an API key name, permissions, or expiration is updated.
 */
@Builder
public record UpdateApiKeyProjectionCommand(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt, Instant emittedAt,
                            UUID userId, String accessId,
                            Permissions permissions,
                            Instant createdAt) implements ApiKeyProjectionCommand {

    public static final String EVENT_TYPE = "apiKey.projection.update";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
