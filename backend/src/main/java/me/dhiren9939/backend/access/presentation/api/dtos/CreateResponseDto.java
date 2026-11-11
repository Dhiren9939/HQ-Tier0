package me.dhiren9939.backend.access.presentation.api.dtos;

import lombok.Builder;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response returned after API key creation; includes the secret for initial delivery.
 */
@Builder
public record CreateResponseDto(UUID apiKeyId, String accessId, String accessSecret, String apiKeyName,
                                Map<Scope, AccessLevel> permissions,
                                Instant createdAt, Instant expiresAt, ApiKeyStatus status) {
}
