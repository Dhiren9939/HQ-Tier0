package me.dhiren9939.api.apikey;

public interface ApiKeyFacade {
    ApiKeyVerifyDto verifyApiKey(String accessId, String accessSecret);
}
