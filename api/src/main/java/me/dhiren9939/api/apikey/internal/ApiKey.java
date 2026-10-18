package me.dhiren9939.api.apikey.internal;

import lombok.Builder;
import me.dhiren9939.api.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/** Result of an API key create, read, update, or delete operation. */
@Builder
public record ApiKey(UUID apiKeyId, UUID userId, String accessId, String accessSecret,
                     String accessSecretHash, String apiKeyName, Permissions permissions,
                     Instant createdAt, Instant expiresAt) {

    static ApiKey fromEntity(ApiKeyEntity entity) {
        return ApiKey.builder()
                .apiKeyId(entity.getApiKeyId())
                .userId(entity.getUserId())
                .accessId(entity.getAccessId())
                .accessSecret(null)
                .accessSecretHash(null)
                .apiKeyName(entity.getApiKeyName())
                .permissions(entity.getPermissions())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    ApiKey withSecret(String accessSecret) {
        return new ApiKey(apiKeyId, userId, accessId, accessSecret, accessSecretHash,
                apiKeyName, permissions, createdAt, expiresAt);
    }

    ApiKey withSecretHash(String accessSecretHash) {
        return new ApiKey(apiKeyId, userId, accessId, accessSecret, accessSecretHash,
                apiKeyName, permissions, createdAt, expiresAt);
    }
}
