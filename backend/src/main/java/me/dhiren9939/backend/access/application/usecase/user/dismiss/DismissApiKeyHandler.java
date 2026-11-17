package me.dhiren9939.backend.access.application.usecase.user.dismiss;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.transaction.RetriedTransaction;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
class DismissApiKeyHandler implements DismissApiKeyUseCase {
    private final ApiKeys apiKeys;
    private final RetriedTransaction transactions;

    @Override
    public void handle(DismissApiKeyCommand command) {
        transactions.execute(() -> {
            ApiKey key = apiKeys.requireOwned(command.userId(), command.apiKeyId());

            key.dismiss();
            apiKeys.delete(key);
        });
    }
}
