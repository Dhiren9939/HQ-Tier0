package me.dhiren9939.api.apikey.internal.dto;

import lombok.Builder;
import me.dhiren9939.api.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Response containing API key metadata without exposing its secret.
 */
@Builder
public record GetResponseDto(UUID apiKeyId, String accessId, String apiKeyName,
                             Permissions permissions,
                             Instant createdAt, Instant expiresAt) {
}
