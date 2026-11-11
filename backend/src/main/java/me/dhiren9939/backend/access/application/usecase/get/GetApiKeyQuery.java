package me.dhiren9939.backend.access.application.usecase.get;

import java.util.UUID;

public record GetApiKeyQuery(UUID userId, UUID apiKeyId) {
}
