package me.dhiren9939.backend.access.presentation.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response containing API key metadata without exposing its secret.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetResponseDto(UUID apiKeyId, String accessId, String apiKeyName,
                             Map<Scope, AccessLevel> permissions,
                             Instant createdAt, Instant expiresAt, ApiKeyState state) {
}
