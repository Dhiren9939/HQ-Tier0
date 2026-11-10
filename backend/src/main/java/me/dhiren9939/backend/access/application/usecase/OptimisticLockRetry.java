package me.dhiren9939.backend.access.application.usecase;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/** Applies Spring Framework retry advice to transactional optimistic-lock attempts. */
@Service
class OptimisticLockRetry {

    @Retryable(includes = ObjectOptimisticLockingFailureException.class, maxRetries = 2)
    public <T> T execute(Supplier<T> operation) {
        return operation.get();
    }
}
