package me.dhiren9939.api.apikey.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.events.ApiKeyCreated;
import me.dhiren9939.api.apikey.events.ApiKeyDeleted;
import me.dhiren9939.api.apikey.events.ApiKeyUpdated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class ApiKeyEventEmitterService {

    private final EventOutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    void emitCreated(ApiKeyEntity entity){
        ApiKeyCreated created = ApiKeyCreated.builder()
                .apiKeyName(entity.getApiKeyName())
                .apiKeyId(entity.getApiKeyId())
                .userId(entity.getUserId())
                .accessId(entity.getAccessId())
                .accessSecretHash(entity.getAccessSecretHash())
                .ingestionPermission(entity.getIngestionPermission())
                .tenantPermission(entity.getTenantPermission())
                .channelPermission(entity.getChannelPermission())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();

        outboxRepository.save(EventOutboxEntity.from(created));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void emitUpdated(ApiKeyEntity entity){
        ApiKeyUpdated updated = ApiKeyUpdated.builder()
                .apiKeyName(entity.getApiKeyName())
                .apiKeyId(entity.getApiKeyId())
                .userId(entity.getUserId())
                .accessId(entity.getAccessId())
                .ingestionPermission(entity.getIngestionPermission())
                .tenantPermission(entity.getTenantPermission())
                .channelPermission(entity.getChannelPermission())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();

        outboxRepository.save(EventOutboxEntity.from(updated));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void emitDeleted(ApiKeyEntity entity){
        ApiKeyDeleted deleted = ApiKeyDeleted.builder()
                .apiKeyName(entity.getApiKeyName())
                .apiKeyId(entity.getApiKeyId())
                .userId(entity.getUserId())
                .accessId(entity.getAccessId())
                .ingestionPermission(entity.getIngestionPermission())
                .tenantPermission(entity.getTenantPermission())
                .channelPermission(entity.getChannelPermission())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();

        outboxRepository.save(EventOutboxEntity.from(deleted));
    }
}
