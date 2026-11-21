package me.dhiren9939.backend.access.domain.exceptions;

/** Indicates a permission the scopes do not accept: a level its scope does not allow, or no scope at all. */
public class InvalidPermissionsException extends InvalidApiKeyException {

    /** A level its scope does not allow. */
    public InvalidPermissionsException(String message) {
        super(ApiKeyViolation.INVALID_SCOPE_LEVEL, message);
    }

    private InvalidPermissionsException(ApiKeyViolation violation) {
        super(violation);
    }

    /** A permission without a scope. */
    public static InvalidPermissionsException scopeRequired() {
        return new InvalidPermissionsException(ApiKeyViolation.PERMISSION_SCOPE_REQUIRED);
    }
}
