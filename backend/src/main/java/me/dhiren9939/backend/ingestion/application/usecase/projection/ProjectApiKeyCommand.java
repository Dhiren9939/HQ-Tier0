package me.dhiren9939.backend.ingestion.application.usecase.projection;

import me.dhiren9939.backend.ingestion.domain.projection.ApiKeySnapshot;

import java.util.UUID;

/**
 * Apply one published snapshot of an API key to the projection.
 *
 * @param eventId  the event that carried the snapshot; only used in logs
 * @param snapshot the key's state at its version
 */
public record ProjectApiKeyCommand(UUID eventId, ApiKeySnapshot snapshot) {

    public ProjectApiKeyCommand {
        if (eventId == null)
            throw new IllegalArgumentException("Project API key command: eventId is required");
        if (snapshot == null)
            throw new IllegalArgumentException("Project API key command: snapshot is required");
    }
}
