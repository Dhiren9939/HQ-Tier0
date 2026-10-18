package me.dhiren9939.api.apikey.events;

import lombok.Builder;
import me.dhiren9939.api.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an API key name, permissions, or expiration is updated.
 */
@Builder
public record ApiKeyUpdated(String apiKeyName, UUID apiKeyId, UUID userId, String accessId,
                            Permissions permissions,
                            Instant createdAt, Instant expiresAt) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.updated";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
