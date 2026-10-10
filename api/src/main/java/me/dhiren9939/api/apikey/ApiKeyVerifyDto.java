package me.dhiren9939.api.apikey;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ApiKeyVerifyDto(UUID apiKeyId, UUID userId,
                              ApiKeyPermissions.Ingestion ingestionPermission,
                              ApiKeyPermissions.Tenant tenantPermission,
                              ApiKeyPermissions.Channel channelPermission, Instant createdAt, Instant expiresAt) {
}
