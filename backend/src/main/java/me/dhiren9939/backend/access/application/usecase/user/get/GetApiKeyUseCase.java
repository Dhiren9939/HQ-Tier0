package me.dhiren9939.backend.access.application.usecase.user.get;

import me.dhiren9939.backend.access.application.model.ApiKeyView;

public interface GetApiKeyUseCase {
    ApiKeyView handle(GetApiKeyQuery query);
}
