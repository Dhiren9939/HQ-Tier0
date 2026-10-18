package me.dhiren9939.api.apikey.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Delegates API key credential generation to the configured versioned provider.
 */
@Slf4j
@Service
class ApiKeyCreationService {

    private final ApiKeyCreationProvider provider;

    ApiKeyCreationService(@Qualifier("creationV1") ApiKeyCreationProvider apiKeyCreationProvider){
        this.provider = apiKeyCreationProvider;
    }

    ApiKeyTriplet createApiKeyTriplet(){
        return provider.createApiKeyTriplet();
    }
}
