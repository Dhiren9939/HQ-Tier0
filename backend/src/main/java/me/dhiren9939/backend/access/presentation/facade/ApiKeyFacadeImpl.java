package me.dhiren9939.backend.access.presentation.facade;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.usecase.verify.VerifiedApiKey;
import me.dhiren9939.backend.access.application.usecase.verify.VerifyApiKeyQuery;
import me.dhiren9939.backend.access.application.usecase.verify.VerifyApiKeyUseCase;
import me.dhiren9939.backend.shared.access.ApiKeyFacade;
import me.dhiren9939.backend.shared.access.ApiKeyVerifyDto;
import org.springframework.stereotype.Component;

/** In-process entry point other modules use to verify credentials; translates to the verify use case. */
@Component
@RequiredArgsConstructor
class ApiKeyFacadeImpl implements ApiKeyFacade {
    private final VerifyApiKeyUseCase verifyApiKey;

    @Override
    public ApiKeyVerifyDto verifyApiKey(String accessId, String accessSecret) {
        VerifiedApiKey verified = verifyApiKey.handle(new VerifyApiKeyQuery(accessId, accessSecret));
        return ApiKeyVerifyDto.builder()
                .apiKeyId(verified.apiKeyId())
                .apiKeyName(verified.apiKeyName())
                .userId(verified.userId())
                .permissions(verified.permissions())
                .createdAt(verified.createdAt())
                .expiresAt(verified.expiresAt())
                .build();
    }
}
