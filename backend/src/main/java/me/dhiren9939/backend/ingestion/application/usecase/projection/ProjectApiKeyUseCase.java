package me.dhiren9939.backend.ingestion.application.usecase.projection;

import me.dhiren9939.backend.ingestion.domain.projection.ProjectionResult;

/** Keeps Ingestion's projection of API keys up to date, one published snapshot at a time. */
public interface ProjectApiKeyUseCase {
    ProjectionResult handle(ProjectApiKeyCommand command);
}
