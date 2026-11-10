package me.dhiren9939.backend.access.application.usecase;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.DismissApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.DismissApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.domain.ApiKey;
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

            switch (key.dismiss()) {
                case CLEARED -> apiKeys.save(key);
                case REMOVED -> apiKeys.delete(key);
            }
        });
    }
}
