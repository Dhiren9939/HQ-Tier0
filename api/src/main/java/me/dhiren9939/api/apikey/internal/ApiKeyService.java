package me.dhiren9939.api.apikey.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyPermissions;
import me.dhiren9939.api.apikey.ApiKeyVerifyDto;
import me.dhiren9939.api.apikey.internal.dto.CreateResponseDto;
import me.dhiren9939.api.apikey.internal.dto.GetResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

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
                                   ApiKeyPermissions.Ingestion ingestionPerm,
                                   ApiKeyPermissions.Tenant tenantPerm,
                                   ApiKeyPermissions.Channel channelPerm) {
        ApiKeyTriplet apiKeyTriplet = creationService.createApiKeyTriplet();

        Instant createdAt = Instant.now(clock);
        Instant expiresAt = apiKeyExpiry.getExpiry(createdAt, duration);

        ApiKeyEntity entity = ApiKeyEntity.builder()
                .apiKeyName(apiKeyName)
                .userId(userId)
                .accessId(apiKeyTriplet.accessId())
                .accessSecretHash(apiKeyTriplet.secretHash())
                .ingestionPermission(ingestionPerm)
                .tenantPermission(tenantPerm)
                .channelPermission(channelPerm)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();

        apiKeyRepository.save(entity);

        CreateResponseDto dto = CreateResponseDto.builder()
                .apiKeyName(apiKeyName)
                .apiKeyId(entity.getApiKeyId())
                .accessId(apiKeyTriplet.accessId())
                .accessSecret(apiKeyTriplet.accessSecret())
                .ingestionPermission(ingestionPerm)
                .tenantPermission(tenantPerm)
                .channelPermission(channelPerm)
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
                .ingestionPermission(entity.getIngestionPermission())
                .tenantPermission(entity.getTenantPermission())
                .channelPermission(entity.getChannelPermission())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    @Transactional
    GetResponseDto updateApiKey(UUID userId, UUID apiKeyId, String apiKeyName, ApiKeyExpiry.Duration duration,
                                ApiKeyPermissions.Ingestion ingestionPerm,
                                ApiKeyPermissions.Tenant tenantPerm,
                                ApiKeyPermissions.Channel channelPerm) {
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

        if (ingestionPerm != null && ingestionPerm != entity.getIngestionPermission()) {
            entity.setIngestionPermission(ingestionPerm);
            changed = true;
        }

        if (tenantPerm != null && tenantPerm != entity.getTenantPermission()) {
            entity.setTenantPermission(tenantPerm);
            changed = true;
        }

        if (channelPerm != null && channelPerm != entity.getChannelPermission()) {
            entity.setChannelPermission(channelPerm);
            changed = true;
        }

        GetResponseDto dto = GetResponseDto.builder()
                .apiKeyId(apiKeyId)
                .apiKeyName(entity.getApiKeyName())
                .accessId(entity.getAccessId())
                .ingestionPermission(entity.getIngestionPermission())
                .tenantPermission(entity.getTenantPermission())
                .channelPermission(entity.getChannelPermission())
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
        ApiKey apiKey = parserService.parse(accessId, accessSecret);
        apiKey = verificationService.verify(apiKey);

        return ApiKeyVerifyDto.builder()
                .apiKeyId(apiKey.getApiKeyId())
                .apiKeyName(apiKey.getApiKeyName())
                .userId(apiKey.getUserId())
                .ingestionPermission(apiKey.getIngestionPermission())
                .tenantPermission(apiKey.getTenantPermission())
                .channelPermission(apiKey.getChannelPermission())
                .createdAt(apiKey.getCreatedAt())
                .expiresAt(apiKey.getExpiresAt())
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
