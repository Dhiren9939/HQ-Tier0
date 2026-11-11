package me.dhiren9939.backend.access.infrastructure.persistence.repositories;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository backing {@link ApiKeysAdapter}. */
interface JpaApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByAccessId(String accessId);
}
