package me.dhiren9939.backend.access.application.usecase;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.RejectChangeCommand;
import me.dhiren9939.backend.access.application.port.in.RejectChangeUseCase;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.domain.ApiKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class RejectChangeHandler implements RejectChangeUseCase {
    private final ApiKeys apiKeys;

    @Override
    @Transactional
    public void handle(RejectChangeCommand command) {
        ApiKey key = apiKeys.require(command.apiKeyId());
        key.reject(command.revision(), command.reason());
        apiKeys.save(key);
    }
}
