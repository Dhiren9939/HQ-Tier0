package me.dhiren9939.backend.access.application.usecase;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.ConfirmChangeCommand;
import me.dhiren9939.backend.access.application.port.in.ConfirmChangeUseCase;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.domain.ApiKey;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class ConfirmChangeHandler implements ConfirmChangeUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;
    private final RetriedTransaction transactions;

    @Override
    public void handle(ConfirmChangeCommand command) {
        transactions.execute(() -> {
            ApiKey key = apiKeys.require(command.apiKeyId());
            key.confirm(command.revision(), clock.instant());
            apiKeys.save(key);
        });
    }
}
