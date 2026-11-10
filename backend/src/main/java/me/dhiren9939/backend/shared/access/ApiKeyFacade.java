package me.dhiren9939.backend.shared.access;

/**
 * Public entry point for verifying API key credentials.
 */
public interface ApiKeyFacade {
    ApiKeyVerifyDto verifyApiKey(String accessId, String accessSecret);
}
