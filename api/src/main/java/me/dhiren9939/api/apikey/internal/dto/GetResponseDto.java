package me.dhiren9939.api.apikey.internal.dto;

import lombok.Builder;
import me.dhiren9939.api.apikey.ApiKeyPermissions;

import java.time.Instant;
import java.util.UUID;

@Builder
public record GetResponseDto(UUID apiKeyId, String accessId,
                             ApiKeyPermissions.Ingestion ingestionPermission,
                             ApiKeyPermissions.Tenant tenantPermission,
                             ApiKeyPermissions.Channel channelPermission,
                             Instant createdAt, Instant expiresAt) {
}
