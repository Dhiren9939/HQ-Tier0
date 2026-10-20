package me.dhiren9939.api.apikey.internal;

import lombok.Builder;
import me.dhiren9939.api.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/** Result of an API key create, read, update, or delete operation. */
@Builder
public record ApiKeyDto(UUID apiKeyId, UUID userId, String accessId, String accessSecret,
                     String accessSecretHash, String apiKeyName, Permissions permissions,
                     Instant createdAt, Instant expiresAt, ApiKeyUpdateStatus updateStatus) {

    static ApiKeyDto fromEntity(ApiKeyEntity entity) {
        return ApiKeyDto.builder()
                .apiKeyId(entity.getApiKeyId())
                .userId(entity.getUserId())
                .accessId(entity.getAccessId())
                .accessSecret(null)
                .accessSecretHash(null)
                .apiKeyName(entity.getApiKeyName())
                .permissions(entity.getPermissions())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .updateStatus(entity.getNext() == null
                        ? null
                        : entity.getNext().getUpdateStatus())
                .build();
    }

    ApiKeyDto withSecret(String accessSecret) {
        return new ApiKeyDto(apiKeyId, userId, accessId, accessSecret, accessSecretHash,
                apiKeyName, permissions, createdAt, expiresAt, updateStatus);
    }

    ApiKeyDto withSecretHash(String accessSecretHash) {
        return new ApiKeyDto(apiKeyId, userId, accessId, accessSecret, accessSecretHash,
                apiKeyName, permissions, createdAt, expiresAt, updateStatus);
    }
}
