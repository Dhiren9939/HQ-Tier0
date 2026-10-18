package me.dhiren9939.api.apikey.internal.dto;

import lombok.Builder;
import me.dhiren9939.api.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Response returned after API key creation; includes the secret for initial delivery.
 */
@Builder
public record CreateResponseDto(UUID apiKeyId, String accessId, String accessSecret, String apiKeyName,
                                Permissions permissions,
                                Instant createdAt, Instant expiresAt) {
}
