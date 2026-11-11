package me.dhiren9939.backend.access.application.usecase.update;

import me.dhiren9939.backend.access.application.model.ApiKeyView;

public interface UpdateApiKeyUseCase {
    ApiKeyView handle(UpdateApiKeyCommand command);
}
