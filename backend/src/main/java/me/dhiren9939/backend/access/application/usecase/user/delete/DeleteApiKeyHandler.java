package me.dhiren9939.backend.access.application.usecase.user.delete;

import me.dhiren9939.backend.access.application.transaction.RetriedTransaction;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class DeleteApiKeyHandler implements DeleteApiKeyUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;
    private final RetriedTransaction transactions;

    @Override
    public void handle(DeleteApiKeyCommand command) {
        transactions.execute(() -> {
            ApiKey key = apiKeys.requireOwned(command.userId(), command.apiKeyId());
            key.requestDeletion(clock.instant());
            apiKeys.save(key);
        });
    }
}
