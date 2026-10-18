package me.dhiren9939.api.apikey.internal;

import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.Permissions;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class ApiKeyPersistenceService {
    private static final int MAX_UPDATE_ATTEMPTS = 3;

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyEventEmitterService emitterService;
    private final TransactionTemplate transactionTemplate;

    public ApiKeyPersistenceService(ApiKeyRepository apiKeyRepository,
                                    ApiKeyEventEmitterService emitterService,
                                    PlatformTransactionManager transactionManager) {
        this.apiKeyRepository = apiKeyRepository;
        this.emitterService = emitterService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    ApiKey create(UUID userId, String apiKeyName, ApiKeyTriplet apiKeyTriplet,
                  Permissions permissions, Instant createdAt, Instant expiresAt) {
        ApiKeyEntity entity = ApiKeyEntity.builder()
                .userId(userId)
                .accessId(apiKeyTriplet.accessId())
                .accessSecretHash(apiKeyTriplet.secretHash())
                .apiKeyName(apiKeyName)
                .permissions(permissions)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();

        apiKeyRepository.save(entity);
        emitterService.emitCreated(entity);
        return ApiKey.fromEntity(entity).withSecret(apiKeyTriplet.accessSecret());
    }

    @Transactional(readOnly = true)
    ApiKey get(UUID userId, UUID apiKeyId) {
        return ApiKey.fromEntity(findOwned(userId, apiKeyId));
    }

    @Transactional(readOnly = true)
    public ApiKey getForVerification(String accessId) {
        ApiKeyEntity entity = apiKeyRepository.findByAccessId(accessId)
                .orElseThrow(() -> {
                    log.warn("No ApiKey found for accessId {}", accessId);
                    return new ApiKeyNotFoundException("API key not found");
                });
        return ApiKey.fromEntity(entity).withSecretHash(entity.getAccessSecretHash());
    }

    ApiKey update(UUID userId, UUID apiKeyId, String apiKeyName, Instant expiresAt,
                  boolean updateExpiry, Permissions permissionUpdates) {
        for (int attempt = 1; attempt <= MAX_UPDATE_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> updateOnce(
                        userId, apiKeyId, apiKeyName, expiresAt, updateExpiry, permissionUpdates
                ));
            } catch (ObjectOptimisticLockingFailureException conflict) {
                if (attempt == MAX_UPDATE_ATTEMPTS) {
                    log.warn("API key update retries exhausted");
                    throw new ApiKeyUpdateFailure("Failed to update the API key");
                }
            }
        }

        log.warn("API key update retry loop exited unexpectedly");
        throw new IllegalStateException("Failed to update the API key");
    }

    private ApiKey updateOnce(UUID userId, UUID apiKeyId, String apiKeyName, Instant expiresAt,
                              boolean updateExpiry, Permissions permissionUpdates) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);
        boolean changed = false;

        if (apiKeyName != null && !Objects.equals(entity.getApiKeyName(), apiKeyName)) {
            entity.setApiKeyName(apiKeyName);
            changed = true;
        }
        if (updateExpiry) {
            entity.setExpiresAt(expiresAt);
            changed = true;
        }

        Permissions current = entity.getPermissions();
        Permissions updated = new Permissions(
                permissionUpdates.ingestionPermission() != null
                        ? permissionUpdates.ingestionPermission() : current.ingestionPermission(),
                permissionUpdates.tenantPermission() != null
                        ? permissionUpdates.tenantPermission() : current.tenantPermission(),
                permissionUpdates.channelPermission() != null
                        ? permissionUpdates.channelPermission() : current.channelPermission()
        );
        if (!updated.equals(current)) {
            entity.setPermissions(updated);
            changed = true;
        }

        if (changed) {
            apiKeyRepository.save(entity);
            emitterService.emitUpdated(entity);
        }
        return ApiKey.fromEntity(entity);
    }

    @Transactional
    void delete(UUID userId, UUID apiKeyId) {
        try {
            ApiKeyEntity entity = findOwned(userId, apiKeyId);
            apiKeyRepository.delete(entity);
            emitterService.emitDeleted(entity);
        } catch (ApiKeyNotFoundException _){
            log.warn("Delete tried on missing API key");
        }
    }

    private ApiKeyEntity findOwned(UUID userId, UUID apiKeyId) {
        return apiKeyRepository.findById(apiKeyId)
                .filter(key -> key.getUserId().equals(userId))
                .orElseThrow(() -> {
                    log.warn("Could not find ApiKey with given userId");
                    return new ApiKeyNotFoundException("API key not found");
                });
    }
}
