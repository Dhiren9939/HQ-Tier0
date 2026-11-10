package me.dhiren9939.backend.access.application.usecase;

import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;
import me.dhiren9939.backend.access.domain.ApiKey;
import me.dhiren9939.backend.shared.access.events.domain.FailureReason;
import me.dhiren9939.backend.access.domain.IssuedCredential;
import me.dhiren9939.backend.shared.access.Permissions;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Shared constants and key factories for the use-case tests. */
final class Fixtures {
    static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    static final Instant ONE_MONTH_FROM_NOW = Instant.parse("2026-02-15T12:00:00Z");
    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    static final String NAME = "test-key";
    static final Permissions NO_PERMISSIONS = Permissions.none();
    static final IssuedCredential CREDENTIAL = new IssuedCredential("V1_acc", "secret_raw", "hash-of-secret_raw");

    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private Fixtures() {
    }

    /** Creation requested, not yet confirmed. */
    static ApiKey provisioning() {
        return ApiKey.issue(USER_ID, NAME, CREDENTIAL, NO_PERMISSIONS, NOW, ONE_MONTH_FROM_NOW);
    }

    static ApiKey active() {
        ApiKey key = provisioning();
        key.confirm(1, NOW);
        return key;
    }

    static ApiKey provisioningFailed() {
        ApiKey key = provisioning();
        key.reject(1, FailureReason.REJECTED, NOW);
        return key;
    }

    static ApiKey changePending() {
        ApiKey key = active();
        key.requestDeletion(NOW);
        return key;
    }

    static ApiKey changeFailed() {
        ApiKey key = changePending();
        key.reject(2, FailureReason.REJECTED, NOW);
        return key;
    }

    static ApiKey deleted() {
        ApiKey key = changePending();
        key.confirm(2, NOW);
        return key;
    }

    /** Transactions that do nothing: handler tests are about the logic, not the database. */
    static RetriedTransaction transactions() {
        return transactions(new OptimisticLockRetry());
    }

    static RetriedTransaction transactions(OptimisticLockRetry retry) {
        return new RetriedTransaction(retry, new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }

    /** A retry that always loses the race, as when every attempt meets a concurrent writer. */
    static OptimisticLockRetry alwaysConflicts() {
        return new OptimisticLockRetry() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> operation) {
                throw new ObjectOptimisticLockingFailureException("conflict", null);
            }
        };
    }
}
