package me.dhiren9939.api.apikey;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Returns the authenticated API key owner, permissions, and lifecycle timestamps.
 */
@Builder
public record ApiKeyVerifyDto(UUID apiKeyId, UUID userId, String apiKeyName,
                              ApiKeyPermissions.Ingestion ingestionPermission,
                              ApiKeyPermissions.Tenant tenantPermission,
                              ApiKeyPermissions.Channel channelPermission, Instant createdAt, Instant expiresAt) {
}
