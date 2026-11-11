package me.dhiren9939.backend.access.application.usecase.get;

import me.dhiren9939.backend.access.application.model.ApiKeyView;

public interface GetApiKeyUseCase {
    ApiKeyView handle(GetApiKeyQuery query);
}
