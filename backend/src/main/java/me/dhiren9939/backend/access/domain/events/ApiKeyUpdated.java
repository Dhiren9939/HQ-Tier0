package me.dhiren9939.backend.access.domain.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * The data plane must update its projection of the key (name, permissions or expiry).
 */
public record ApiKeyUpdated(String apiKeyName, UUID apiKeyId, long version, Instant occurredAt,
                            UUID userId, String accessId,
                            Permissions permissions,
                            Instant createdAt, Instant expiresAt) implements ApiKeyEvent {
}
