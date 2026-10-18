package me.dhiren9939.api.apikey.internal;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.apikey.ApiKeyVerifyDto;
import me.dhiren9939.api.apikey.Permissions;
import me.dhiren9939.api.apikey.internal.dto.CreateResponseDto;
import me.dhiren9939.api.apikey.internal.dto.GetResponseDto;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Coordinates API key creation, lookup, updates, deletion, and credential verification.
 */
@Service
@RequiredArgsConstructor
class ApiKeyService {
    private final Clock clock;
    private final ApiKeyExpiry apiKeyExpiry;
    private final ApiKeyParserService parserService;
    private final ApiKeyCreationService creationService;
    private final ApiKeyVerificationService verificationService;
    private final ApiKeyPersistenceService persistenceService;

    CreateResponseDto createApiKey(UUID userId, String apiKeyName, ApiKeyExpiry.Duration duration,
                                   Permissions permissions) {
        ApiKeyTriplet apiKeyTriplet = creationService.createApiKeyTriplet();

        Instant createdAt = Instant.now(clock);
        Instant expiresAt = apiKeyExpiry.getExpiry(createdAt, duration);

        ApiKey apiKey = persistenceService.create(
                userId, apiKeyName, apiKeyTriplet, permissions, createdAt, expiresAt
        );

        return toCreateResponseDto(apiKey);
    }

    GetResponseDto getApiKey(UUID userId, UUID apiKeyId) {
        return toGetResponseDto(persistenceService.get(userId, apiKeyId));
    }

    GetResponseDto updateApiKey(UUID userId, UUID apiKeyId, String apiKeyName, ApiKeyExpiry.Duration duration,
                                Permissions permissions) {
        Instant expiresAt = duration == null ? null : apiKeyExpiry.getExpiry(Instant.now(clock), duration);
        ApiKey apiKey = persistenceService.update(
                userId, apiKeyId, apiKeyName, expiresAt, duration != null, permissions
        );

        return toGetResponseDto(apiKey);
    }

    void deleteApiKey(UUID userId, UUID apiKeyId) {
        persistenceService.delete(userId, apiKeyId);
    }

    ApiKeyVerifyDto verifyApiKey(String accessId, String accessSecret) {
        ApiKeyToken apiKeyToken = parserService.parse(accessId, accessSecret);
        apiKeyToken = verificationService.verify(apiKeyToken);

        return ApiKeyVerifyDto.builder()
                .apiKeyId(apiKeyToken.getApiKeyId())
                .apiKeyName(apiKeyToken.getApiKeyName())
                .userId(apiKeyToken.getUserId())
                .permissions(apiKeyToken.getPermissions())
                .createdAt(apiKeyToken.getCreatedAt())
                .expiresAt(apiKeyToken.getExpiresAt())
                .build();
    }

    private CreateResponseDto toCreateResponseDto(ApiKey apiKey) {
        return CreateResponseDto.builder()
                .apiKeyId(apiKey.apiKeyId())
                .accessId(apiKey.accessId())
                .accessSecret(apiKey.accessSecret())
                .apiKeyName(apiKey.apiKeyName())
                .permissions(apiKey.permissions())
                .createdAt(apiKey.createdAt())
                .expiresAt(apiKey.expiresAt())
                .build();
    }

    private GetResponseDto toGetResponseDto(ApiKey apiKey) {
        return GetResponseDto.builder()
                .apiKeyId(apiKey.apiKeyId())
                .accessId(apiKey.accessId())
                .apiKeyName(apiKey.apiKeyName())
                .permissions(apiKey.permissions())
                .createdAt(apiKey.createdAt())
                .expiresAt(apiKey.expiresAt())
                .build();
    }
}
