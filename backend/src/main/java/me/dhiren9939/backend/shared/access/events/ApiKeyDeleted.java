package me.dhiren9939.backend.shared.access.events;

import lombok.Builder;
import me.dhiren9939.backend.shared.access.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an API key is deleted.
 */
@Builder
public record ApiKeyDeleted(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt, Instant emittedAt,
                            UUID userId, String accessId,
                            Permissions permissions,
                            Instant createdAt) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.deleted";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
