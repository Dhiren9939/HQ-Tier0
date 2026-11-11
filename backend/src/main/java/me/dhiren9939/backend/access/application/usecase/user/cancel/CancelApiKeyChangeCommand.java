package me.dhiren9939.backend.access.application.usecase.user.cancel;

import java.util.UUID;

/** The owner cancels the change that is still waiting for the data plane. */
public record CancelApiKeyChangeCommand(UUID userId, UUID apiKeyId) {
}
