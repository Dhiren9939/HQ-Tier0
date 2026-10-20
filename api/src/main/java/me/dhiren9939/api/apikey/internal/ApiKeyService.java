package me.dhiren9939.api.apikey.internal;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.apikey.Permissions;
import me.dhiren9939.api.apikey.internal.dto.CreateResponseDto;
import me.dhiren9939.api.apikey.internal.dto.GetResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ApiKeyCreationService creationService;
    private final ApiKeyPersistenceService persistenceService;

    @Transactional
    CreateResponseDto createApiKey(UUID userId, String apiKeyName, ApiKeyExpiry.Duration duration,
                                   Permissions permissions) {
        ApiKeyTriplet apiKeyTriplet = creationService.createApiKeyTriplet();

        Instant createdAt = Instant.now(clock);
        Instant expiresAt = apiKeyExpiry.getExpiry(createdAt, duration);

        ApiKeyDto apiKey = persistenceService.create(
                userId, apiKeyName, apiKeyTriplet, permissions, createdAt, expiresAt
        );

        return toCreateResponseDto(apiKey);
    }

    GetResponseDto getApiKey(UUID userId, UUID apiKeyId) {
        return toGetResponseDto(persistenceService.get(userId, apiKeyId));
    }

    @Transactional
    GetResponseDto updateApiKey(UUID userId, UUID apiKeyId, String apiKeyName, ApiKeyExpiry.Duration duration,
                                Permissions permissions) {
        ApiKeyDto apiKey = persistenceService.update(
                userId, apiKeyId, apiKeyName, duration, permissions
        );

        return toGetResponseDto(apiKey);
    }

    @Transactional
    void acknowledgeFailure(UUID userId, UUID apiKeyId) {
        persistenceService.acknowledgeFailure(userId, apiKeyId);
    }

    @Transactional
    void deleteApiKey(UUID userId, UUID apiKeyId) {
        ApiKeyDto apiKey = persistenceService.delete(userId, apiKeyId);
    }

    private CreateResponseDto toCreateResponseDto(ApiKeyDto apiKey) {
        return CreateResponseDto.builder()
                .apiKeyId(apiKey.apiKeyId())
                .accessId(apiKey.accessId())
                .accessSecret(apiKey.accessSecret())
                .apiKeyName(apiKey.apiKeyName())
                .permissions(apiKey.permissions())
                .createdAt(apiKey.createdAt())
                .expiresAt(apiKey.expiresAt())
                .updateStatus(apiKey.updateStatus())
                .build();
    }

    private GetResponseDto toGetResponseDto(ApiKeyDto apiKey) {
        return GetResponseDto.builder()
                .apiKeyId(apiKey.apiKeyId())
                .accessId(apiKey.accessId())
                .apiKeyName(apiKey.apiKeyName())
                .permissions(apiKey.permissions())
                .createdAt(apiKey.createdAt())
                .expiresAt(apiKey.expiresAt())
                .updateStatus(apiKey.updateStatus())
                .build();
    }
}
