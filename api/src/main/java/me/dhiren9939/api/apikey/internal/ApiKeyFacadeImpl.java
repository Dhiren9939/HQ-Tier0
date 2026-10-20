package me.dhiren9939.api.apikey.internal;

import me.dhiren9939.api.apikey.ApiKeyFacade;
import me.dhiren9939.api.apikey.ApiKeyVerifyDto;
import org.springframework.stereotype.Component;

/**
 * Implements API-key verification through the parsing and provider services.
 */
@Component
class ApiKeyFacadeImpl implements ApiKeyFacade {

    private final ApiKeyParserService parserService;
    private final ApiKeyVerificationService verificationService;

    ApiKeyFacadeImpl(ApiKeyParserService parserService, ApiKeyVerificationService verificationService) {
        this.parserService = parserService;
        this.verificationService = verificationService;
    }

    public ApiKeyVerifyDto verifyApiKey(String accessId, String accessSecret) {
        ApiKeyToken token = verificationService.verify(parserService.parse(accessId, accessSecret));
        return ApiKeyVerifyDto.builder()
                .apiKeyId(token.getApiKeyId())
                .apiKeyName(token.getApiKeyName())
                .userId(token.getUserId())
                .permissions(token.getPermissions())
                .createdAt(token.getCreatedAt())
                .expiresAt(token.getExpiresAt())
                .build();
    }
}
