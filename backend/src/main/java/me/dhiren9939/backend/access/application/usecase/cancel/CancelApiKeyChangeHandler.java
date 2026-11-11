package me.dhiren9939.backend.access.application.usecase.cancel;

import me.dhiren9939.backend.access.application.transaction.RetriedTransaction;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class CancelApiKeyChangeHandler implements CancelApiKeyChangeUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;
    private final RetriedTransaction transactions;

    @Override
    public void handle(CancelApiKeyChangeCommand command) {
        transactions.execute(() -> {
            ApiKey key = apiKeys.requireOwned(command.userId(), command.apiKeyId());
            key.cancelPendingChange(clock.instant());
            apiKeys.save(key);
        });
    }
}
