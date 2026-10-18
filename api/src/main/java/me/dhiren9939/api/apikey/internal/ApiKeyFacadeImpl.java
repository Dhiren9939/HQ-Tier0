package me.dhiren9939.api.apikey.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyFacade;
import me.dhiren9939.api.apikey.ApiKeyVerifyDto;
import org.springframework.stereotype.Component;

/**
 * Implements the public API key facade by delegating verification to the service layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ApiKeyFacadeImpl implements ApiKeyFacade {

    private final ApiKeyService apiKeyService;

    public ApiKeyVerifyDto verifyApiKey(String accessId, String accessSecret) {
        return apiKeyService.verifyApiKey(accessId, accessSecret);
    }
}
