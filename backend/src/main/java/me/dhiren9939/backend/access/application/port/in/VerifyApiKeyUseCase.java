package me.dhiren9939.backend.access.application.port.in;

public interface VerifyApiKeyUseCase {
    VerifiedApiKey handle(VerifyApiKeyQuery query);
}
