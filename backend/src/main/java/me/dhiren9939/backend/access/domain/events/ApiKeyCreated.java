package me.dhiren9939.backend.access.domain.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * The data plane must create its projection of the key, including the persisted credential hash.
 */
public record ApiKeyCreated(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt,
                            UUID userId, String accessId,
                            String accessSecretHash,
                            Permissions permissions,
                            Instant createdAt, Instant expiresAt) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.created";

    public String eventType(){
        return EVENT_TYPE;
    }

}
