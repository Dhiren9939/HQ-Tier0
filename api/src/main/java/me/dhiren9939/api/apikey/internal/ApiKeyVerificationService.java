package me.dhiren9939.api.apikey.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyVerificationFailure;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Selects a compatible versioned provider and delegates API key verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ApiKeyVerificationService {
    private final List<ApiKeyVerificationProvider> providers;

    ApiKeyToken verify(ApiKeyToken apiKeyToken){
        ApiKeyVerificationProvider provider = providers.stream()
                .filter(p -> p.supports(apiKeyToken))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Could not find a verification strategy for version {}", apiKeyToken.getKeyVersion());
                    return new ApiKeyVerificationFailure("Could not verify the ApiKey");
                });

        return provider.verify(apiKeyToken);
    }
}
