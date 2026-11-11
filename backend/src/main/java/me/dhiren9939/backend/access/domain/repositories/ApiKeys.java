package me.dhiren9939.backend.access.domain.repositories;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;

import java.util.Optional;
import java.util.UUID;

/** Output port through which use cases load and store {@link ApiKey} aggregates. */
public interface ApiKeys {
    Optional<ApiKey> findById(UUID apiKeyId);

    Optional<ApiKey> findByAccessId(String accessId);

    ApiKey save(ApiKey key);

    void delete(ApiKey key);

    /** The key with this id, or {@link ApiKeyNotFoundException}. */
    default ApiKey require(UUID apiKeyId) {
        return findById(apiKeyId).orElseThrow(ApiKeyNotFoundException::new);
    }

    /** The key with this id if {@code userId} owns it; otherwise {@link ApiKeyNotFoundException}. */
    default ApiKey requireOwned(UUID userId, UUID apiKeyId) {
        return findById(apiKeyId)
                .filter(key -> key.isOwnedBy(userId))
                .orElseThrow(ApiKeyNotFoundException::new);
    }
}
