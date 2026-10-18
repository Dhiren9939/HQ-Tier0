package me.dhiren9939.api.apikey.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyVerifyDto;
import me.dhiren9939.api.apikey.Permissions;
import me.dhiren9939.api.apikey.internal.dto.CreateResponseDto;
import me.dhiren9939.api.apikey.internal.dto.GetResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Coordinates API key creation, lookup, updates, deletion, and credential verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ApiKeyService {
    private final Clock clock;
    private final ApiKeyExpiry apiKeyExpiry;
    private final ApiKeyParserService parserService;
    private final ApiKeyEventEmitterService emitterService;

    private final ApiKeyCreationService creationService;
    private final ApiKeyVerificationService verificationService;

    private final ApiKeyRepository apiKeyRepository;

    @Transactional
    CreateResponseDto createApiKey(UUID userId, String apiKeyName, ApiKeyExpiry.Duration duration,
                                   Permissions permissions) {
        ApiKeyTriplet apiKeyTriplet = creationService.createApiKeyTriplet();

        Instant createdAt = Instant.now(clock);
        Instant expiresAt = apiKeyExpiry.getExpiry(createdAt, duration);

        ApiKeyEntity entity = ApiKeyEntity.builder()
                .apiKeyName(apiKeyName)
                .userId(userId)
                .accessId(apiKeyTriplet.accessId())
                .accessSecretHash(apiKeyTriplet.secretHash())
                .permissions(permissions)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();

        apiKeyRepository.save(entity);

        CreateResponseDto dto = CreateResponseDto.builder()
                .apiKeyName(apiKeyName)
                .apiKeyId(entity.getApiKeyId())
                .accessId(apiKeyTriplet.accessId())
                .accessSecret(apiKeyTriplet.accessSecret())
                .permissions(permissions)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();

        emitterService.emitCreated(entity);
        return dto;
    }

    @Transactional(readOnly = true)
    GetResponseDto getApiKey(UUID userId, UUID apiKeyId) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);

        return GetResponseDto.builder()
                .apiKeyId(apiKeyId)
                .apiKeyName(entity.getApiKeyName())
                .accessId(entity.getAccessId())
                .permissions(entity.getPermissions())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    @Transactional
    GetResponseDto updateApiKey(UUID userId, UUID apiKeyId, String apiKeyName, ApiKeyExpiry.Duration duration,
                                Permissions permissions) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);
        boolean changed = false;

        if (apiKeyName != null && !apiKeyName.equals(entity.getApiKeyName())) {
            entity.setApiKeyName(apiKeyName);
            changed = true;
        }

        if (duration != null) {
            Instant now = Instant.now(clock);
            Instant expiresAt = apiKeyExpiry.getExpiry(now, duration);
            entity.setExpiresAt(expiresAt);
            changed = true;
        }

        Permissions currentPermissions = entity.getPermissions();
        Permissions updatedPermissions = new Permissions(
                permissions.ingestionPermission() != null
                        ? permissions.ingestionPermission()
                        : currentPermissions.ingestionPermission(),
                permissions.tenantPermission() != null
                        ? permissions.tenantPermission()
                        : currentPermissions.tenantPermission(),
                permissions.channelPermission() != null
                        ? permissions.channelPermission()
                        : currentPermissions.channelPermission()
        );

        if (!updatedPermissions.equals(currentPermissions)) {
            entity.setPermissions(updatedPermissions);
            changed = true;
        }

        GetResponseDto dto = GetResponseDto.builder()
                .apiKeyId(apiKeyId)
                .apiKeyName(entity.getApiKeyName())
                .accessId(entity.getAccessId())
                .permissions(entity.getPermissions())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();

        if (changed) {
            apiKeyRepository.save(entity);
            emitterService.emitUpdated(entity);
        }

        return dto;
    }

    @Transactional
    void deleteApiKey(UUID userId, UUID apiKeyId) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);
        apiKeyRepository.delete(entity);
        emitterService.emitDeleted(entity);
    }

    @Transactional(readOnly = true)
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

    private ApiKeyEntity findOwned(UUID userId, UUID apiKeyId) {
        return apiKeyRepository.findById(apiKeyId)
                .filter(key -> key.getUserId().equals(userId))
                .orElseThrow(() -> {
                    log.warn("Could not find ApiKey with given userId");
                    return new NoSuchElementException("API key not found");
                });
    }
}
