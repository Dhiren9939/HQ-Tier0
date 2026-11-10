package me.dhiren9939.backend.access.adapter.out.persistence;

import me.dhiren9939.backend.access.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository backing the {@link ApiKeysAdapter}. */
interface JpaApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByAccessId(String accessId);
}
