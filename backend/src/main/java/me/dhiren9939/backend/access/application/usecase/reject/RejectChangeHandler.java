package me.dhiren9939.backend.access.application.usecase.reject;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeRejected;

import me.dhiren9939.backend.access.application.transaction.RetriedTransaction;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class RejectChangeHandler implements RejectChangeUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;
    private final RetriedTransaction transactions;

    @Override
    public void handle(ApiKeyChangeRejected command) {
        transactions.execute(() -> {
            ApiKey key = apiKeys.require(command.apiKeyId());
            key.reject(command.version(), command.failureReason(), clock.instant());
            apiKeys.save(key);
        });
    }
}
