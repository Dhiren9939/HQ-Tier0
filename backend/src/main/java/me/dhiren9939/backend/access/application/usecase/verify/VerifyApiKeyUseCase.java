package me.dhiren9939.backend.access.application.usecase.verify;


public interface VerifyApiKeyUseCase {
    VerifiedApiKey handle(VerifyApiKeyQuery query);
}
