package me.dhiren9939.api.apikey.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.Permissions;
import me.dhiren9939.api.apikey.events.ApiKeyCreated;
import me.dhiren9939.api.apikey.events.ApiKeyDeleted;
import me.dhiren9939.api.apikey.events.ApiKeyUpdated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Persists API key lifecycle events to the transactional outbox. */
@Slf4j
@Service
@RequiredArgsConstructor
class ApiKeyEventEmitterService {
    private final EventOutboxRepository outboxRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    void emitCreated(ApiKeyEntity entity) {
        ApiKeyNextEntity next = entity.getNext();
        ApiKeyCreated event = ApiKeyCreated.builder()
                .apiKeyName(resolveApiKeyName(entity, next))
                .apiKeyId(entity.getApiKeyId())
                .version(entity.getDesiredVersion())
                .emittedAt(Instant.now(clock))
                .userId(entity.getUserId())
                .accessId(entity.getAccessId())
                .accessSecretHash(entity.getAccessSecretHash())
                .permissions(resolvePermissions(entity, next))
                .createdAt(entity.getCreatedAt())
                .expiresAt(resolveExpiresAt(entity, next))
                .build();
        outboxRepository.save(EventOutboxEntity.from(event));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void emitUpdated(ApiKeyEntity entity) {
        ApiKeyNextEntity next = entity.getNext();
        ApiKeyUpdated event = ApiKeyUpdated.builder()
                .apiKeyName(resolveApiKeyName(entity, next))
                .apiKeyId(entity.getApiKeyId())
                .version(entity.getDesiredVersion())
                .emittedAt(Instant.now(clock))
                .userId(entity.getUserId())
                .accessId(entity.getAccessId())
                .permissions(resolvePermissions(entity, next))
                .createdAt(entity.getCreatedAt())
                .expiresAt(resolveExpiresAt(entity, next))
                .build();
        outboxRepository.save(EventOutboxEntity.from(event));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void emitDeleted(ApiKeyEntity entity) {
        ApiKeyNextEntity next = entity.getNext();
        ApiKeyDeleted event = ApiKeyDeleted.builder()
                .apiKeyName(resolveApiKeyName(entity, next))
                .apiKeyId(entity.getApiKeyId())
                .version(entity.getDesiredVersion())
                .emittedAt(Instant.now(clock))
                .userId(entity.getUserId())
                .accessId(entity.getAccessId())
                .permissions(resolvePermissions(entity, next))
                .createdAt(entity.getCreatedAt())
                .expiresAt(resolveExpiresAt(entity, next))
                .build();
        outboxRepository.save(EventOutboxEntity.from(event));
    }

    private String resolveApiKeyName(ApiKeyEntity entity, ApiKeyNextEntity next) {
        return next != null && next.getApiKeyName() != null ? next.getApiKeyName() : entity.getApiKeyName();
    }

    private Permissions resolvePermissions(ApiKeyEntity entity, ApiKeyNextEntity next) {
        return next != null && next.getPermissions() != null ? next.getPermissions() : entity.getPermissions();
    }

    private Instant resolveExpiresAt(ApiKeyEntity entity, ApiKeyNextEntity next) {
        return next != null ? next.getExpiresAt() : entity.getExpiresAt();
    }
}
