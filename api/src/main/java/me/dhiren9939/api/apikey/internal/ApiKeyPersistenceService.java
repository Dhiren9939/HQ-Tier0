package me.dhiren9939.api.apikey.internal;

import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyPermissions;
import me.dhiren9939.api.apikey.Permissions;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class ApiKeyPersistenceService {
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyNextRepository apiKeyNextRepository;
    private final ApiKeyEventEmitterService eventEmitterService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final OptimisticLockRetry optimisticLockRetry;
    private final ApiKeyExpiry apiKeyExpiry;

    public ApiKeyPersistenceService(ApiKeyRepository apiKeyRepository,
                                    ApiKeyNextRepository apiKeyNextRepository,
                                    ApiKeyEventEmitterService eventEmitterService,
                                    ApiKeyExpiry apiKeyExpiry,
                                    Clock clock,
                                    OptimisticLockRetry optimisticLockRetry,
                                    PlatformTransactionManager transactionManager) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyNextRepository = apiKeyNextRepository;
        this.eventEmitterService = eventEmitterService;
        this.apiKeyExpiry = apiKeyExpiry;
        this.clock = clock;
        this.optimisticLockRetry = optimisticLockRetry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    ApiKeyDto create(UUID userId, String apiKeyName, ApiKeyTriplet apiKeyTriplet,
                     Permissions permissions, Instant createdAt, Instant expiresAt) {

        ApiKeyEntity entity = ApiKeyEntity.builder()
                .userId(userId)
                .apiKeyName(apiKeyName)
                .accessId(apiKeyTriplet.accessId())
                .accessSecretHash(apiKeyTriplet.secretHash())
                .permissions(permissions)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();

        ApiKeyNextEntity nextEntity = ApiKeyNextEntity.builder()
                .updateVersion(entity.getDesiredVersion())
                .updatedAt(createdAt)
                .updateType(ApiKeyUpdateType.UPSERT)
                .updateStatus(ApiKeyUpdateStatus.PENDING)
                .apiKeyName(apiKeyName)
                .permissions(permissions)
                .expiresAt(expiresAt)
                .build();

        setNextWithCleanUp(entity, nextEntity);

        apiKeyNextRepository.save(nextEntity);
        apiKeyRepository.save(entity);

        eventEmitterService.emitCreated(entity);

        return ApiKeyDto.fromEntity(entity)
                .withSecretHash(apiKeyTriplet.secretHash())
                .withSecret(apiKeyTriplet.accessSecret());
    }

    @Transactional(readOnly = true)
    ApiKeyDto get(UUID userId, UUID apiKeyId) {
        return ApiKeyDto.fromEntity(findOwned(userId, apiKeyId));
    }

    @Transactional(readOnly = true)
    public ApiKeyDto getForVerification(String accessId) {
        ApiKeyEntity entity = apiKeyRepository.findByAccessId(accessId)
                .orElseThrow(() -> {
                    log.warn("No ApiKey found for accessId={}", accessId);
                    return new ApiKeyNotFoundException();
                });

        if (entity.getAppliedVersion() == ApiKeyEntity.NON_EXISTENT) {
            log.warn("Verification called for a pending create ApiKey with accessId={}", accessId);
            throw new ApiKeyNotFoundException();
        }
        return ApiKeyDto.fromEntity(entity).withSecretHash(entity.getAccessSecretHash());
    }

    public ApiKeyDto update(UUID userId, UUID apiKeyId, String apiKeyName, ApiKeyExpiry.Duration duration, Permissions permissionUpdates) {
        try {
            return optimisticLockRetry.execute(() -> transactionTemplate.execute(_ -> updateOnce(userId, apiKeyId, apiKeyName, duration, permissionUpdates)));
        } catch (ObjectOptimisticLockingFailureException conflict) {
            log.warn("API key update retries exhausted for apiKeyId={}", apiKeyId, conflict);
            throw new ApiKeyUpdateFailure();
        }
    }

    private ApiKeyDto updateOnce(UUID userId, UUID apiKeyId, String apiKeyName, ApiKeyExpiry.Duration duration, Permissions permissionUpdates) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);

        if (doesntExist(entity))
            throw new ApiKeyNotFoundException("Can't update an API key with pending creation");
        if (hasPendingChanges(entity))
            throw new ApiKeyUpdateFailure("Can't update an API key with pending changes");

        boolean changed = false;
        Instant now = clock.instant();
        String updatedName = entity.getApiKeyName();
        Instant updatedExpiry = entity.getExpiresAt();
        ApiKeyPermissions.Ingestion updatedIngestionPermission = entity.getPermissions().ingestionPermission();
        ApiKeyPermissions.Tenant updatedTenantPermission = entity.getPermissions().tenantPermission();
        ApiKeyPermissions.Channel updatedChannelPermission = entity.getPermissions().channelPermission();

        if (apiKeyName != null && !entity.getApiKeyName().equals(apiKeyName)) {
            updatedName = apiKeyName;
            changed = true;
        }
        if (duration != null && !Objects.equals(entity.getExpiresAt(), apiKeyExpiry.getExpiry(now, duration))) {
            updatedExpiry = apiKeyExpiry.getExpiry(now, duration);
            changed = true;
        }
        if (permissionUpdates != null) {
            if (permissionUpdates.ingestionPermission() != null &&
                    permissionUpdates.ingestionPermission() != entity.getPermissions().ingestionPermission()) {
                updatedIngestionPermission = permissionUpdates.ingestionPermission();
                changed = true;
            }

            if (permissionUpdates.tenantPermission() != null &&
                    permissionUpdates.tenantPermission() != entity.getPermissions().tenantPermission()) {
                updatedTenantPermission = permissionUpdates.tenantPermission();
                changed = true;
            }

            if (permissionUpdates.channelPermission() != null &&
                    permissionUpdates.channelPermission() != entity.getPermissions().channelPermission()) {
                updatedChannelPermission = permissionUpdates.channelPermission();
                changed = true;
            }
        }

        if (!changed)
            return ApiKeyDto.fromEntity(entity);

        long updateVersion = entity.getIssuedVersion() + 1;
        entity.setIssuedVersion(updateVersion);
        entity.setDesiredVersion(updateVersion);

        ApiKeyNextEntity nextEntity = ApiKeyNextEntity.builder()
                .updateVersion(updateVersion)
                .updatedAt(clock.instant())
                .updateType(ApiKeyUpdateType.UPSERT)
                .updateStatus(ApiKeyUpdateStatus.PENDING)
                .apiKeyName(updatedName)
                .permissions(new Permissions(
                        updatedIngestionPermission,
                        updatedTenantPermission,
                        updatedChannelPermission))
                .expiresAt(updatedExpiry)
                .build();

        setNextWithCleanUp(entity, nextEntity);

        apiKeyRepository.save(entity);
        apiKeyNextRepository.save(nextEntity);

        eventEmitterService.emitUpdated(entity);

        return ApiKeyDto.fromEntity(entity);
    }

    @Transactional
    void acknowledgeFailure(UUID userId, UUID apiKeyId) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);

        if(doesntExist(entity))
            throw new ApiKeyNotFoundException();
        if(!hasFailedChange(entity))
            throw new ApiKeyUpdateFailure("API key doesn't have a failure to acknowledge");

        setNextWithCleanUp(entity, null);
    }

    @Transactional
    ApiKeyDto delete(UUID userId, UUID apiKeyId) {
        ApiKeyEntity entity = findOwned(userId, apiKeyId);

        if(doesntExist(entity))
            throw new ApiKeyNotFoundException("Can't delete an API key with pending creation");
        if(hasPendingChanges(entity))
            throw new ApiKeyUpdateFailure("Can't delete an API key with pending changes");

        long updateVersion = entity.getIssuedVersion() + 1;
        entity.setDesiredVersion(updateVersion);
        entity.setIssuedVersion(updateVersion);

        ApiKeyNextEntity nextEntity = ApiKeyNextEntity.builder()
                .updateVersion(updateVersion)
                .updatedAt(clock.instant())
                .updateType(ApiKeyUpdateType.DELETE)
                .updateStatus(ApiKeyUpdateStatus.PENDING)
                .apiKeyName(entity.getApiKeyName())
                .permissions(entity.getPermissions())
                .expiresAt(entity.getExpiresAt())
                .build();

        setNextWithCleanUp(entity, nextEntity);

        eventEmitterService.emitDeleted(entity);

        return ApiKeyDto.fromEntity(entity);
    }

    private ApiKeyEntity findOwned(UUID userId, UUID apiKeyId) {
        return apiKeyRepository.findById(apiKeyId)
                .filter(key -> key.getUserId().equals(userId))
                .orElseThrow(() -> {
                    log.warn("Could not find ApiKey with given userId");
                    return new ApiKeyNotFoundException();
                });
    }

    private boolean doesntExist(ApiKeyEntity entity) {
        return entity.getAppliedVersion() == ApiKeyEntity.NON_EXISTENT;
    }

    private boolean hasPendingChanges(ApiKeyEntity entity) {
        return entity.getNext() != null && entity.getNext().getUpdateStatus() != ApiKeyUpdateStatus.FAILED;
    }

    private boolean hasFailedChange(ApiKeyEntity entity){
        return entity.getNext() != null && entity.getNext().getUpdateStatus() == ApiKeyUpdateStatus.FAILED;
    }

    private void setNextWithCleanUp(ApiKeyEntity entity, ApiKeyNextEntity nextEntity) {
        if (entity.getNext() != null)
            apiKeyNextRepository.delete(entity.getNext());
        entity.setNext(nextEntity);
    }

}
