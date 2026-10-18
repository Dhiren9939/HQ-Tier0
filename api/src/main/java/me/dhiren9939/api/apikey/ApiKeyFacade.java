package me.dhiren9939.api.apikey;

/**
 * Public entry point for verifying API key credentials.
 */
public interface ApiKeyFacade {
    ApiKeyVerifyDto verifyApiKey(String accessId, String accessSecret);
}
