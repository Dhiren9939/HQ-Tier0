package me.dhiren9939.backend.access.application.usecase;

import me.dhiren9939.backend.access.application.events.ApiKeyProjectionMapper;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyProjectionCommand;

import java.util.*;

/**
 * In-memory stand-in for {@link ApiKeys}. Like the real implementation it collects the projection commands a key
 * registered whenever it is saved or deleted, so tests can assert on what would reach the outbox.
 */
public class InMemoryApiKeys implements ApiKeys {
    private final Map<UUID, ApiKey> store = new LinkedHashMap<>();
    public final List<ApiKeyProjectionCommand> publishedEvents = new ArrayList<>();
    public int saves;
    public int deletes;

    /** Puts a key into the store as if it had been persisted earlier; its pending events are discarded. */
    public ApiKey existing(ApiKey key) {
        key.pullDomainEvents();
        store.put(key.getApiKeyId(), key);
        return key;
    }

    public boolean contains(UUID apiKeyId) {
        return store.containsKey(apiKeyId);
    }

    public ApiKey stored(UUID apiKeyId) {
        return store.get(apiKeyId);
    }

    public ApiKey onlyKey() {
        if (store.size() != 1)
            throw new IllegalStateException("Expected exactly one stored key but found " + store.size());
        return store.values().iterator().next();
    }

    @Override
    public Optional<ApiKey> findById(UUID apiKeyId) {
        return Optional.ofNullable(store.get(apiKeyId));
    }

    @Override
    public Optional<ApiKey> findByAccessId(String accessId) {
        return store.values().stream().filter(key -> key.getAccessId().equals(accessId)).findFirst();
    }

    @Override
    public ApiKey save(ApiKey key) {
        saves++;
        store.put(key.getApiKeyId(), key);
        record(key);
        return key;
    }

    private void record(ApiKey key) {
        key.pullDomainEvents().stream()
                .map(ApiKeyProjectionMapper::toProjectionCommand)
                .forEach(publishedEvents::add);
    }

    @Override
    public void delete(ApiKey key) {
        deletes++;
        store.remove(key.getApiKeyId());
        record(key);
    }
}
