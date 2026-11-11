package me.dhiren9939.backend.access.application.usecase.confirm;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeConfirmed;

import me.dhiren9939.backend.access.application.transaction.RetriedTransaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
class ConfirmChangeHandler implements ConfirmChangeUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;
    private final RetriedTransaction transactions;

    @Override
    public void handle(ApiKeyChangeConfirmed command) {
        transactions.execute(() -> {
            ApiKey key = apiKeys.require(command.apiKeyId());
            key.confirm(command.version(), clock.instant());
            apiKeys.save(key);
            log.info("API key {} change at revision {} confirmed by {}", command.apiKeyId(), command.version(), command.serviceId());
        });
    }
}
