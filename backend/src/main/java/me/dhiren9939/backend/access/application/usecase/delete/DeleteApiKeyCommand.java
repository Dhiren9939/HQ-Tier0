package me.dhiren9939.backend.access.application.usecase.delete;

import java.util.UUID;

public record DeleteApiKeyCommand(UUID userId, UUID apiKeyId) {
}
