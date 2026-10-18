package me.dhiren9939.api.apikey.events;

import me.dhiren9939.api.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Common event contract for API key creation, update, and deletion events.
 */
public sealed interface ApiKeyEvent permits ApiKeyCreated, ApiKeyUpdated, ApiKeyDeleted {
    String eventType();

    String apiKeyName();

    UUID apiKeyId();

    UUID userId();

    String accessId();

    Permissions permissions();

    Instant createdAt();

    Instant expiresAt();
}
