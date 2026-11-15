package me.dhiren9939.backend.common.access;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The authenticated API key's owner, permissions and lifecycle timestamps. Carries no secret. */
public record ApiKeyView(UUID apiKeyId, UUID userId, String apiKeyName,
                         Map<String, String> permissions, Instant createdAt, Instant expiresAt) {
}
