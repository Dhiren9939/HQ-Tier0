package me.dhiren9939.api.apikey;

/**
 * Indicates that an API key could not be authenticated or is no longer valid.
 */
public class ApiKeyVerificationFailure extends RuntimeException {
    public ApiKeyVerificationFailure(String message) {
        super(message);
    }
}
