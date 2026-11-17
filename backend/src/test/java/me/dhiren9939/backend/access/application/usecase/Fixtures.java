package me.dhiren9939.backend.access.application.usecase;

import me.dhiren9939.backend.access.application.transaction.OptimisticLockRetry;
import me.dhiren9939.backend.access.application.transaction.RetriedTransaction;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.IssuedCredential;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;

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
public final class Fixtures {
    public static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    public static final Instant ONE_MONTH_FROM_NOW = Instant.parse("2026-02-15T12:00:00Z");
    public static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    public static final String NAME = "test-key";
    public static final Permissions NO_PERMISSIONS = Permissions.none();
    public static final IssuedCredential CREDENTIAL = new IssuedCredential("V1_acc", "secret_raw", "hash-of-secret_raw");

    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private Fixtures() {
    }

    /** An issued key whose creation event is already collected. */
    public static ApiKey active() {
        ApiKey key = ApiKey.issue(USER_ID, NAME, CREDENTIAL, NO_PERMISSIONS, NOW, ONE_MONTH_FROM_NOW);
        key.pullDomainEvents();
        return key;
    }

    public static ApiKey deleted() {
        ApiKey key = active();
        key.delete(NOW);
        key.pullDomainEvents();
        return key;
    }

    /** Transactions that do nothing: handler tests are about the logic, not the database. */
    public static RetriedTransaction transactions() {
        return transactions(new OptimisticLockRetry());
    }

    public static RetriedTransaction transactions(OptimisticLockRetry retry) {
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
    public static OptimisticLockRetry alwaysConflicts() {
        return new OptimisticLockRetry() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> operation) {
                throw new ObjectOptimisticLockingFailureException("conflict", null);
            }
        };
    }
}
