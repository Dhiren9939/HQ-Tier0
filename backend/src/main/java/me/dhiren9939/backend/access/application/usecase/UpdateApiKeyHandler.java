package me.dhiren9939.backend.access.application.usecase;

import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.access.application.port.in.ApiKeyView;
import me.dhiren9939.backend.access.application.port.in.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.UpdateApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.domain.ApiKey;
import me.dhiren9939.backend.access.domain.ApiKeyChanges;
import me.dhiren9939.backend.access.domain.ApiKeyUpdateFailure;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Each attempt runs in its own transaction so an optimistic-lock conflict can be retried against fresh
 * state; the outbox row of a successful attempt is written just before that transaction commits.
 */
@Slf4j
@Service
class UpdateApiKeyHandler implements UpdateApiKeyUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;
    private final OptimisticLockRetry optimisticLockRetry;
    private final TransactionTemplate transactionTemplate;

    UpdateApiKeyHandler(ApiKeys apiKeys, Clock clock, OptimisticLockRetry optimisticLockRetry,
                        PlatformTransactionManager transactionManager) {
        this.apiKeys = apiKeys;
        this.clock = clock;
        this.optimisticLockRetry = optimisticLockRetry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public ApiKeyView handle(UpdateApiKeyCommand command) {
        try {
            return optimisticLockRetry.execute(() -> transactionTemplate.execute(_ -> updateOnce(command)));
        } catch (ObjectOptimisticLockingFailureException conflict) {
            log.warn("API key update retries exhausted for apiKeyId={}", command.apiKeyId(), conflict);
            throw new ApiKeyUpdateFailure();
        }
    }

    private ApiKeyView updateOnce(UpdateApiKeyCommand command) {
        ApiKey key = apiKeys.requireOwned(command.userId(), command.apiKeyId());

        ApiKeyChanges changes = new ApiKeyChanges(command.name(), command.lifetime(), command.permissions());
        if (key.requestUpdate(changes, clock.instant()))
            apiKeys.save(key);

        return ApiKeyView.of(key);
    }
}
