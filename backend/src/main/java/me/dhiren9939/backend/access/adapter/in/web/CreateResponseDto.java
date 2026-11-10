package me.dhiren9939.backend.access.adapter.in.web;

import lombok.Builder;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;
import me.dhiren9939.backend.access.domain.ChangeStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response returned after API key creation; includes the secret for initial delivery.
 */
@Builder
public record CreateResponseDto(UUID apiKeyId, String accessId, String accessSecret, String apiKeyName,
                                Map<Scope, AccessLevel> permissions,
                                Instant createdAt, Instant expiresAt, ChangeStatus updateStatus) {
}
