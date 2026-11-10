package me.dhiren9939.backend.access.application.port.in;

public interface UpdateApiKeyUseCase {
    ApiKeyView handle(UpdateApiKeyCommand command);
}
