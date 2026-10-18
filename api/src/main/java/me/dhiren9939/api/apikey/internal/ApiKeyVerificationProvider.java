package me.dhiren9939.api.apikey.internal;

/**
 * Strategy interface for verifying API keys of a particular credential format.
 */
public interface ApiKeyVerificationProvider {
    boolean supports(ApiKey apiKey);
    ApiKey verify(ApiKey apiKey);
}
