package me.dhiren9939.backend.access.application.usecase;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.DismissApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.DismissApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.domain.ApiKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class DismissApiKeyHandler implements DismissApiKeyUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;

    @Override
    @Transactional
    public void handle(DismissApiKeyCommand command) {
        ApiKey key = apiKeys.requireOwned(command.userId(), command.apiKeyId());

        switch (key.dismiss(clock.instant())) {
            case CLEARED -> apiKeys.save(key);
            case REMOVED -> apiKeys.delete(key);
        }
    }
}
