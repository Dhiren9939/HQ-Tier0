package me.dhiren9939.api.apikey.internal;

/**
 * Strategy interface for generating an API key identifier, secret, and stored hash.
 */
public interface ApiKeyCreationProvider {
    ApiKeyTriplet createApiKeyTriplet();
}
