package me.dhiren9939.backend.access.application.usecase.update;

import me.dhiren9939.backend.access.application.transaction.RetriedTransaction;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyChanges;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class UpdateApiKeyHandler implements UpdateApiKeyUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;
    private final RetriedTransaction transactions;

    @Override
    public ApiKeyView handle(UpdateApiKeyCommand command) {
        return transactions.execute(() -> updateOnce(command));
    }

    private ApiKeyView updateOnce(UpdateApiKeyCommand command) {
        ApiKey key = apiKeys.requireOwned(command.userId(), command.apiKeyId());

        ApiKeyChanges changes = new ApiKeyChanges(command.name(), command.lifetime(), command.permissions());
        if (key.requestUpdate(changes, clock.instant()))
            apiKeys.save(key);

        return ApiKeyView.of(key);
    }
}
