package me.dhiren9939.api.apikey.internal;

public interface ApiKeyVerificationProvider {
    boolean supports(ApiKey apiKey);
    ApiKey verify(ApiKey apiKey);
}
