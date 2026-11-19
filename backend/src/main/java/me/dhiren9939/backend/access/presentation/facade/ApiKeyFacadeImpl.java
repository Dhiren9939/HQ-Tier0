package me.dhiren9939.backend.access.presentation.facade;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.application.usecase.service.verify.ApiKeyVerificationFailure;
import me.dhiren9939.backend.access.application.usecase.service.verify.VerifiedApiKey;
import me.dhiren9939.backend.access.application.usecase.service.verify.VerifyApiKeyUseCase;
import me.dhiren9939.backend.common.access.ApiKeyVerificationFacade;
import me.dhiren9939.backend.common.access.ApiKeyVerifyResult;
import me.dhiren9939.backend.common.access.ApiKeyView;
import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
                    permissions(verified.permissions()),
                    verified.createdAt(),
                    verified.expiresAt()));
        } catch (ApiKeyVerificationFailure failure) {
            return new ApiKeyVerifyResult.Failure(failure.code(), failure.getMessage());
        }
    }

    /** Scope name to level name, ordered by scope declaration; an absent scope means no access. */
    private static Map<String, String> permissions(Permissions permissions) {
        Map<String, String> byName = new LinkedHashMap<>();
        permissions.asMap().forEach((scope, level) -> byName.put(scope.name(), level.name()));
        return Collections.unmodifiableMap(byName);
    }
}
