package me.dhiren9939.backend.access.application.usecase.user.get;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class GetApiKeyHandler implements GetApiKeyUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ApiKeyView handle(GetApiKeyQuery query) {
        return ApiKeyView.of(apiKeys.requireOwned(query.userId(), query.apiKeyId()), clock.instant());
    }
}
