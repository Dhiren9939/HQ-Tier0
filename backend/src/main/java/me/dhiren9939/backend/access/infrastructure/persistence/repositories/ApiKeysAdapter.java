package me.dhiren9939.backend.access.infrastructure.persistence.repositories;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.events.ApiKeyProjectionMapper;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of {@link ApiKeys}. After storing or removing a key it maps the domain events the
 * aggregate registered to projection commands and writes them straight into the outbox, in the caller's
 * transaction: if the outbox row cannot be written the key change rolls back with it.
 */
@Repository
@RequiredArgsConstructor
class ApiKeysAdapter implements ApiKeys {
    private final JpaApiKeyRepository repository;
    private final EventOutboxRepository outboxRepository;

    @Override
    public Optional<ApiKey> findById(UUID apiKeyId) {
        return repository.findById(apiKeyId);
    }

    @Override
    public Optional<ApiKey> findByAccessId(String accessId) {
        return repository.findByAccessId(accessId);
    }

    @Override
    public ApiKey save(ApiKey key) {
        ApiKey saved = repository.save(key);
        writeEventsOf(key);
        return saved;
    }

    @Override
    public void delete(ApiKey key) {
        repository.delete(key);
        writeEventsOf(key);
    }

    private void writeEventsOf(ApiKey key) {
        key.pullDomainEvents().stream()
                .map(ApiKeyProjectionMapper::toProjectionCommand)
                .forEach(event -> outboxRepository.save(EventOutboxEntity.of(event)));
    }
}
