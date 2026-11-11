package me.dhiren9939.backend.access.application.usecase.verify;

import me.dhiren9939.backend.shared.access.Permissions;

import java.time.Instant;
import java.util.UUID;

/** An authenticated key's identity, permissions and lifecycle timestamps. Immutable; carries no secret. */
public record VerifiedApiKey(UUID apiKeyId, UUID userId, String apiKeyName, Permissions permissions,
                             Instant createdAt, Instant expiresAt) {
}
