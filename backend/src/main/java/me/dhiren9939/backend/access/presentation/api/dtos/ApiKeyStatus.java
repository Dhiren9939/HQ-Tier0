package me.dhiren9939.backend.access.presentation.api.dtos;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;

/**
 * What an API key's owner is told about where the key stands. Derived from the internal {@link ApiKeyState}, so
 * the internal states can change without breaking clients.
 */
public enum ApiKeyStatus {
    ACTIVE,
    EXPIRED,
    DELETED;

    public static ApiKeyStatus of(ApiKeyState state) {
        return switch (state) {
            case ACTIVE -> ACTIVE;
            case EXPIRED -> EXPIRED;
            case DELETED -> DELETED;
        };
    }
}
