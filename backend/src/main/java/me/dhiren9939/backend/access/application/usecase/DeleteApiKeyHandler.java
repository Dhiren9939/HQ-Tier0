package me.dhiren9939.backend.access.application.usecase;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.DeleteApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.DeleteApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.domain.ApiKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class DeleteApiKeyHandler implements DeleteApiKeyUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;

    @Override
    @Transactional
    public void handle(DeleteApiKeyCommand command) {
        ApiKey key = apiKeys.requireOwned(command.userId(), command.apiKeyId());
        key.requestDeletion(clock.instant());
        apiKeys.save(key);
    }
}
