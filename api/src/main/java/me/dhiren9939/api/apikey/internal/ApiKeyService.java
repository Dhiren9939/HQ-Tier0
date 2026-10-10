package me.dhiren9939.api.apikey.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyPermissions;
import me.dhiren9939.api.apikey.ApiKeyVerifyDto;
import me.dhiren9939.api.apikey.internal.dto.CreateResponseDto;
import me.dhiren9939.api.apikey.internal.dto.GetResponseDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class ApiKeyService {
    private final ApiKeyExpiry apiKeyExpiry;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyParserService apiKeyParserService;
    private final ApiKeyCreationService apiKeyCreationService;
    private final ApiKeyVerificationService apiKeyVerificationService;

    CreateResponseDto createApiKey(UUID userId, String duration, String ingestionPermVal, String tenantPermVal, String channelPermVal) {
        ApiKeyTriplet apiKeyTriplet = apiKeyCreationService.createApiKeyTriplet();

        Instant createdAt = Instant.now();
        Instant expiresAt = apiKeyExpiry.getExpiry(createdAt, ApiKeyExpiry.Duration.valueOf(duration));

        ApiKeyPermissions.Ingestion ingestionPerm = ApiKeyPermissions.Ingestion.valueOf(ingestionPermVal);
        ApiKeyPermissions.Tenant tenantPerm = ApiKeyPermissions.Tenant.valueOf(tenantPermVal);
        ApiKeyPermissions.Channel channelPerm = ApiKeyPermissions.Channel.valueOf(channelPermVal);

        ApiKeyEntity entity = ApiKeyEntity.builder()
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

        return CreateResponseDto.builder()
                .apiKeyId(entity.getApiKeyId())
                .accessId(apiKeyTriplet.accessId())
                .accessSecret(apiKeyTriplet.accessSecret())
                .ingestionPermission(ingestionPerm)
                .tenantPermission(tenantPerm)
                .channelPermission(channelPerm)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();
    }

    GetResponseDto getApiKey(UUID userId, UUID apiKeyId) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);

        return GetResponseDto.builder()
                .apiKeyId(apiKeyId)
                .accessId(entity.getAccessId())
                .ingestionPermission(entity.getIngestionPermission())
                .tenantPermission(entity.getTenantPermission())
                .channelPermission(entity.getChannelPermission())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    GetResponseDto updateApiKey(UUID userId, UUID apiKeyId, String duration, String ingestionPermVal, String tenantPermVal, String channelPermVal) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);

        if (duration != null) {
            Instant now = Instant.now();
            Instant expiresAt = apiKeyExpiry.getExpiry(now, ApiKeyExpiry.Duration.valueOf(duration));
            entity.setExpiresAt(expiresAt);
        }

        if(ingestionPermVal != null){
            entity.setIngestionPermission(ApiKeyPermissions.Ingestion.valueOf(ingestionPermVal));
        }

        if(tenantPermVal != null){
            entity.setTenantPermission(ApiKeyPermissions.Tenant.valueOf(tenantPermVal));
        }

        if(channelPermVal != null){
            entity.setChannelPermission(ApiKeyPermissions.Channel.valueOf(channelPermVal));
        }

        apiKeyRepository.save(entity);

        return GetResponseDto.builder()
                .apiKeyId(apiKeyId)
                .accessId(entity.getAccessId())
                .ingestionPermission(entity.getIngestionPermission())
                .tenantPermission(entity.getTenantPermission())
                .channelPermission(entity.getChannelPermission())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    void deleteApiKey(UUID userId, UUID apiKeyId) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);
        apiKeyRepository.delete(entity);
    }

    ApiKeyVerifyDto verifyApiKey(String accessId, String accessSecret) {
        ApiKey apiKey = apiKeyParserService.parse(accessId, accessSecret);
        apiKey = apiKeyVerificationService.verify(apiKey);

        return ApiKeyVerifyDto.builder()
                .apiKeyId(apiKey.getApiKeyId())
                .userId(apiKey.getUserId())
                .ingestionPermission(apiKey.getIngestionPermission())
                .tenantPermission(apiKey.getTenantPermission())
                .channelPermission(apiKey.getChannelPermission())
                .createdAt(apiKey.getCreatedAt())
                .expiresAt(apiKey.getExpiresAt())
                .build();
    }

    private ApiKeyEntity findOwned(UUID userId, UUID apiKeyId){
        return apiKeyRepository.findById(apiKeyId)
                .filter(key -> key.getUserId().equals(userId))
                .orElseThrow(() -> {
                    log.warn("Could not find ApiKey with given userId");
                    return new NoSuchElementException("API key not found");
                });
    }
}
