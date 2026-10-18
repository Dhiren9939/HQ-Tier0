package me.dhiren9939.api.apikey.events;

import lombok.Builder;
import me.dhiren9939.api.apikey.ApiKeyPermissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an API key is created, including its persisted credential hash.
 */
@Builder
public record ApiKeyCreated(String apiKeyName, UUID apiKeyId, UUID userId, String accessId,
                            String accessSecretHash,
                            ApiKeyPermissions.Ingestion ingestionPermission,
                            ApiKeyPermissions.Tenant tenantPermission,
                            ApiKeyPermissions.Channel channelPermission,
                            Instant createdAt, Instant expiresAt) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.created";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
