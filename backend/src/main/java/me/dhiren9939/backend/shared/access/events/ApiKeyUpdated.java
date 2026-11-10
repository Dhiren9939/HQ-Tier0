package me.dhiren9939.backend.shared.access.events;

import lombok.Builder;
import me.dhiren9939.backend.shared.access.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an API key name, permissions, or expiration is updated.
 */
@Builder
public record ApiKeyUpdated(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt, Instant emittedAt,
                            UUID userId, String accessId,
                            Permissions permissions,
                            Instant createdAt) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.updated";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
