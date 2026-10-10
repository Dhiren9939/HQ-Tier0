package me.dhiren9939.api.apikey;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyInternalDto(UUID apiKeyId, UUID userId, String accessId, String accessSecret,
                                ApiKeyPermissions.Ingestion ingestionPermission, ApiKeyPermissions.Tenant tenantPermission,
                                ApiKeyPermissions.Channel channelPermission, Instant createdAt, Instant expiresAt) {
}
