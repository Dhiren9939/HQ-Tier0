package me.dhiren9939.backend.ingestion.application.usecase.projection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.ingestion.domain.projection.ApiKeySnapshot;
import me.dhiren9939.backend.ingestion.domain.projection.ProjectedApiKey;
import me.dhiren9939.backend.ingestion.domain.projection.ProjectionResult;
import me.dhiren9939.backend.ingestion.domain.repositories.ProjectedApiKeys;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one snapshot in one transaction: a key seen for the first time is created from it, a known key applies it
 * by the version rules of {@link ProjectedApiKey}. The key is saved only when it changed, so duplicates and late
 * snapshots cost one read.
 *
 * <p>Not a Spring bean yet: it becomes one together with the persistence adapter for {@link ProjectedApiKeys}.</p>
 */
@Slf4j
@RequiredArgsConstructor
class ProjectApiKeyHandler implements ProjectApiKeyUseCase {
    private final ProjectedApiKeys apiKeys;

    @Override
    @Transactional
    public ProjectionResult handle(ProjectApiKeyCommand command) {
        ApiKeySnapshot snapshot = command.snapshot();

        ProjectionResult result = apiKeys.findById(snapshot.apiKeyId())
                .map(known -> applyToKnownKey(known, snapshot))
                .orElseGet(() -> createKey(snapshot));

        logResult(command, result);
        return result;
    }

    private ProjectionResult applyToKnownKey(ProjectedApiKey key, ApiKeySnapshot snapshot) {
        ProjectionResult result = key.apply(snapshot);
        if (result.changed())
            apiKeys.save(key);
        return result;
    }

    private ProjectionResult createKey(ApiKeySnapshot snapshot) {
        ProjectedApiKey key = ProjectedApiKey.first(snapshot);
        apiKeys.save(key);
        return key.firstResult();
    }

    private static void logResult(ProjectApiKeyCommand command, ProjectionResult result) {
        ApiKeySnapshot snapshot = command.snapshot();
        if (result.changed())
            log.info("API key projection {}: apiKeyId {} version {} (event {})",
                    result, snapshot.apiKeyId(), snapshot.version(), command.eventId());
        else
            log.debug("API key projection ignored a {} snapshot: apiKeyId {} version {} (event {})",
                    result, snapshot.apiKeyId(), snapshot.version(), command.eventId());
    }
}
