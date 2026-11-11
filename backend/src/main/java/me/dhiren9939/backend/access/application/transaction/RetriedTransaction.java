package me.dhiren9939.backend.access.application.transaction;

import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Runs a write on an API key in its own transaction and retries it when another writer got there first.
 * Every attempt is a fresh {@code REQUIRES_NEW} transaction, so a retry re-reads the key and the domain rules
 * decide the outcome against its current state. When the retries run out the caller gets an
 * {@link ApiKeyUpdateFailure}, which the web layer reports as a conflict. The outbox rows of a successful
 * attempt are written in the same transaction as the key change.
 */
@Slf4j
@Service
public class RetriedTransaction {
    private final OptimisticLockRetry optimisticLockRetry;
    private final TransactionTemplate transactionTemplate;

    public RetriedTransaction(OptimisticLockRetry optimisticLockRetry, PlatformTransactionManager transactionManager) {
        this.optimisticLockRetry = optimisticLockRetry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(Supplier<T> work) {
        try {
            return optimisticLockRetry.execute(() -> transactionTemplate.execute(_ -> work.get()));
        } catch (ObjectOptimisticLockingFailureException conflict) {
            log.warn("API key write retries exhausted", conflict);
            throw new ApiKeyUpdateFailure();
        }
    }

    public void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
