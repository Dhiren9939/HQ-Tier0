package me.dhiren9939.backend.access.presentation.facade;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.events.ApiKeyProjectionMapper;
import me.dhiren9939.backend.access.application.usecase.service.verify.ApiKeyVerificationFailure;
import me.dhiren9939.backend.access.application.usecase.service.verify.VerifiedApiKey;
import me.dhiren9939.backend.access.application.usecase.service.verify.VerifyApiKeyUseCase;
import me.dhiren9939.backend.common.access.ApiKeyVerificationFacade;
import me.dhiren9939.backend.common.access.ApiKeyVerifyResult;
import me.dhiren9939.backend.common.access.ApiKeyView;
import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;
import org.springframework.stereotype.Component;

/** In-process entry point other modules use to verify credentials; translates to the verify use case. */
@Component
@RequiredArgsConstructor
class ApiKeyFacadeImpl implements ApiKeyVerificationFacade {
    private final VerifyApiKeyUseCase verifyApiKey;

    @Override
    public ApiKeyVerifyResult verifyApiKey(VerifyApiKeyQuery query) {
        try {
            VerifiedApiKey verified = verifyApiKey.handle(query);
            return new ApiKeyVerifyResult.Success(new ApiKeyView(
                    verified.apiKeyId(),
                    verified.userId(),
                    verified.apiKeyName(),
                    ApiKeyProjectionMapper.toWirePermissions(verified.permissions()),
                    verified.createdAt(),
                    verified.expiresAt()));
        } catch (ApiKeyVerificationFailure failure) {
            return new ApiKeyVerifyResult.Failure(failure.code(), failure.getMessage());
        }
    }
}
