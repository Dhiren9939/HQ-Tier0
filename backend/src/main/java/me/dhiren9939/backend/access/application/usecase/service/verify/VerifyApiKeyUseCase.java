package me.dhiren9939.backend.access.application.usecase.service.verify;


public interface VerifyApiKeyUseCase {
    VerifiedApiKey handle(VerifyApiKeyQuery query);
}
