package me.dhiren9939.backend.access.application.port.in;

public interface GetApiKeyUseCase {
    ApiKeyView handle(GetApiKeyQuery query);
}
