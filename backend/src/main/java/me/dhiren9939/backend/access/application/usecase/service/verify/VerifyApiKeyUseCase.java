package me.dhiren9939.backend.access.application.usecase.service.verify;


import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;

public interface VerifyApiKeyUseCase {
    VerifiedApiKey handle(VerifyApiKeyQuery query);
}
