package me.dhiren9939.backend.shared.access;

/**
 * Indicates that an API key could not be authenticated or is no longer valid.
 */
public class ApiKeyVerificationFailure extends RuntimeException {
    public ApiKeyVerificationFailure(String message) {
        super(message);
    }
}
