package me.dhiren9939.api.apikey.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import me.dhiren9939.api.apikey.Permissions;
import me.dhiren9939.api.apikey.internal.ApiKeyUpdateStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response containing API key metadata without exposing its secret.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetResponseDto(UUID apiKeyId, String accessId, String apiKeyName,
                             Permissions permissions,
                             Instant createdAt, Instant expiresAt, ApiKeyUpdateStatus updateStatus) {
}
