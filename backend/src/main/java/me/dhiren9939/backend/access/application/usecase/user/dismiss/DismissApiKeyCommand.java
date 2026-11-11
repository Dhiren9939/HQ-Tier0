package me.dhiren9939.backend.access.application.usecase.user.dismiss;

import java.util.UUID;

public record DismissApiKeyCommand(UUID userId, UUID apiKeyId) {
}
