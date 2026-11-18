package me.dhiren9939.backend.access.domain.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * The data plane must delete its projection of the key.
 */
public record ApiKeyDeleted(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt,
                            UUID userId, String accessId,
                            String accessSecretHash,
                            Permissions permissions,
                            Instant createdAt, Instant expiresAt) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.deleted";

    public String eventType(){
        return EVENT_TYPE;
    }

}
