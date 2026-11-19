package me.dhiren9939.backend.ingestion.domain.repositories;

import me.dhiren9939.backend.ingestion.domain.projection.ProjectedApiKey;

import java.util.Optional;
import java.util.UUID;

/** The projection's store. Deleted keys are kept as tombstones, so {@link #findById} also returns them. */
public interface ProjectedApiKeys {
    Optional<ProjectedApiKey> findById(UUID apiKeyId);

    void save(ProjectedApiKey key);
}
