package me.dhiren9939.api.apikey.events;

import me.dhiren9939.api.apikey.ApiKeyPermissions;

import java.time.Instant;
import java.util.UUID;

public sealed interface ApiKeyEvent permits ApiKeyCreated, ApiKeyUpdated, ApiKeyDeleted {
    String eventType();

    String apiKeyName();

    UUID apiKeyId();

    UUID userId();

    String accessId();

    ApiKeyPermissions.Ingestion ingestionPermission();

    ApiKeyPermissions.Tenant tenantPermission();

    ApiKeyPermissions.Channel channelPermission();

    Instant createdAt();

    Instant expiresAt();
}
