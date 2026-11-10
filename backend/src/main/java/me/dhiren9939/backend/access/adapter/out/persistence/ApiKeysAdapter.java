package me.dhiren9939.backend.access.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.domain.ApiKey;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of the {@link ApiKeys} domain port. After storing or removing a key it publishes the
 * domain events the aggregate registered; {@link OutboxEventListener} records them in the same transaction.
 */
@Repository
@RequiredArgsConstructor
class ApiKeysAdapter implements ApiKeys {
    private final JpaApiKeyRepository repository;
    private final ApplicationEventPublisher eventPublisher;

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
        publishEventsOf(key);
        return saved;
    }

    @Override
    public void delete(ApiKey key) {
        repository.delete(key);
        publishEventsOf(key);
    }

    private void publishEventsOf(ApiKey key) {
        key.pullDomainEvents().forEach(eventPublisher::publishEvent);
    }
}
