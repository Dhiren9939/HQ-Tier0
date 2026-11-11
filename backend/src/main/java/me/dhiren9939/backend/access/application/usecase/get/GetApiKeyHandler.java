package me.dhiren9939.backend.access.application.usecase.get;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class GetApiKeyHandler implements GetApiKeyUseCase {
    private final ApiKeys apiKeys;

    @Override
    @Transactional(readOnly = true)
    public ApiKeyView handle(GetApiKeyQuery query) {
        return ApiKeyView.of(apiKeys.requireOwned(query.userId(), query.apiKeyId()));
    }
}
