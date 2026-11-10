package me.dhiren9939.backend.access.application.usecase;

import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.domain.ApiKey;
import me.dhiren9939.backend.shared.access.events.domain.ApiKeyEvent;
import me.dhiren9939.backend.shared.access.events.domain.ApiKeyProjectionCommand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory stand-in for the {@link ApiKeys} port. Like the real adapter it collects the domain events a key
 * registered whenever it is saved or deleted, so tests can assert on what would reach the outbox.
 */
class InMemoryApiKeys implements ApiKeys {
    private final Map<UUID, ApiKey> store = new LinkedHashMap<>();
    /** Facts about how changes ended (confirmed, rejected). */
    final List<ApiKeyEvent> changeEvents = new ArrayList<>();
    /** The projection commands only, which is what most tests are about. */
    final List<ApiKeyEvent> publishedEvents = new ArrayList<>();
    int saves;
    int deletes;

    /** Puts a key into the store as if it had been persisted earlier; its pending events are discarded. */
    ApiKey existing(ApiKey key) {
        key.pullDomainEvents();
        store.put(key.getApiKeyId(), key);
        return key;
    }

    boolean contains(UUID apiKeyId) {
        return store.containsKey(apiKeyId);
    }

    ApiKey stored(UUID apiKeyId) {
        return store.get(apiKeyId);
    }

    ApiKey onlyKey() {
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
        for (ApiKeyEvent event : key.pullDomainEvents()) {
            if (event instanceof ApiKeyProjectionCommand)
                publishedEvents.add(event);
            else
                changeEvents.add(event);
        }
    }

    @Override
    public void delete(ApiKey key) {
        deletes++;
        store.remove(key.getApiKeyId());
        record(key);
    }
}
