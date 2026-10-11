package me.dhiren9939.api.apikey.events;

import lombok.Builder;
import me.dhiren9939.api.apikey.ApiKeyPermissions;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ApiKeyDeleted(String apiKeyName, UUID apiKeyId, UUID userId, String accessId,
                            ApiKeyPermissions.Ingestion ingestionPermission,
                            ApiKeyPermissions.Tenant tenantPermission,
                            ApiKeyPermissions.Channel channelPermission,
                            Instant createdAt, Instant expiresAt) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.deleted";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
