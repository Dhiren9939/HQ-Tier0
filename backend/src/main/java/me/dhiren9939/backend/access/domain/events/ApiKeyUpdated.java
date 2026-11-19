package me.dhiren9939.backend.access.domain.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * The data plane must update its projection of the key (name, permissions or expiry).
 */
public record ApiKeyUpdated(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt,
                            UUID userId, String accessId,
                            String accessSecretHash,
                            Permissions permissions,
                            Instant createdAt, Instant expiresAt) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.updated";

    /** Refuses, naming the field, an event that is incomplete or does not fit the key's columns. */
    public ApiKeyUpdated {
        ApiKeyEventRules.check(EVENT_TYPE, apiKeyName, apiKeyId, version, occurredAt, userId, accessId, accessSecretHash,
                permissions, createdAt, expiresAt);
    }

    public String eventType(){
        return EVENT_TYPE;
    }

}
