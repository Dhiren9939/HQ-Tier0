package me.dhiren9939.backend.access.application.model;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ChangeKind;
import me.dhiren9939.backend.shared.access.Permissions;

import java.time.Instant;
import java.util.UUID;

/** Secret-free read model of an API key as its owner sees it. {@code state} and {@code changeKind} (null when nothing is unsettled) are internal; the presentation layer derives what clients see. */
public record ApiKeyView(UUID apiKeyId, UUID userId, String accessId, String apiKeyName,
                         Permissions permissions, Instant createdAt, Instant expiresAt,
                         ApiKeyState state, ChangeKind changeKind) {

    public static ApiKeyView of(ApiKey key) {
        return new ApiKeyView(
                key.getApiKeyId(),
                key.getUserId(),
                key.getAccessId(),
                key.getApiKeyName(),
                key.getPermissions(),
                key.getCreatedAt(),
                key.getExpiresAt(),
                key.state(),
                key.getPendingChange() == null ? null : key.getPendingChange().getKind());
    }
}
