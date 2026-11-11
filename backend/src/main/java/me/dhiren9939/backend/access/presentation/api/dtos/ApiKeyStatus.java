package me.dhiren9939.backend.access.presentation.api.dtos;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ChangeKind;

/**
 * What an API key's owner is told about where the key stands. Derived from the internal {@link ApiKeyState} and
 * the kind of the unsettled change, so the internal states can change without breaking clients. It says what
 * is happening and whether it failed, never why.
 */
public enum ApiKeyStatus {
    CREATING,
    CREATION_FAILED,
    ACTIVE,
    UPDATING,
    UPDATE_FAILED,
    DELETING,
    DELETE_FAILED,
    DELETED;

    public static ApiKeyStatus of(ApiKeyState state, ChangeKind kind) {
        return switch (state) {
            case PROVISIONING -> CREATING;
            case PROVISIONING_FAILED -> CREATION_FAILED;
            case ACTIVE -> ACTIVE;
            case CHANGE_PENDING -> kind == ChangeKind.DELETE ? DELETING : UPDATING;
            case CHANGE_FAILED -> kind == ChangeKind.DELETE ? DELETE_FAILED : UPDATE_FAILED;
            case DELETED -> DELETED;
        };
    }
}
