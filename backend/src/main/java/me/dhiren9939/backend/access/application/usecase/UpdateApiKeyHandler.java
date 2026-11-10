package me.dhiren9939.backend.access.application.usecase;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.ApiKeyView;
import me.dhiren9939.backend.access.application.port.in.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.UpdateApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.domain.ApiKey;
import me.dhiren9939.backend.access.domain.ApiKeyChanges;
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
