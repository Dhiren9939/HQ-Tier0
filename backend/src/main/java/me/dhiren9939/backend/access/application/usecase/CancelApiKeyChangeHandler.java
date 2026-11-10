package me.dhiren9939.backend.access.application.usecase;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.CancelApiKeyChangeCommand;
import me.dhiren9939.backend.access.application.port.in.CancelApiKeyChangeUseCase;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.domain.ApiKey;
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
