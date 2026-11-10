package me.dhiren9939.backend.access.application.port.in;

import java.util.UUID;

public record GetApiKeyQuery(UUID userId, UUID apiKeyId) {
}
