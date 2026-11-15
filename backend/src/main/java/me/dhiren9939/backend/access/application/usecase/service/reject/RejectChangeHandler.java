package me.dhiren9939.backend.access.application.usecase.service.reject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.access.application.transaction.RetriedTransaction;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.FailureReason;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.common.access.events.inbound.ApiKeyChangeFailureReason;
import me.dhiren9939.backend.common.access.events.inbound.ApiKeyChangeRejected;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
class RejectChangeHandler implements RejectChangeUseCase {
    private final ApiKeys apiKeys;
    private final Clock clock;
    private final RetriedTransaction transactions;

    @Override
    public void handle(ApiKeyChangeRejected command) {
        transactions.execute(() -> {
            ApiKey key = apiKeys.require(command.apiKeyId());
            key.reject(command.version(), toDomain(command.failureReason()), clock.instant());
            apiKeys.save(key);
            log.info("API key {} change at revision {} rejected by {}", command.apiKeyId(), command.version(), command.issuer());
        });
    }

    private static FailureReason toDomain(ApiKeyChangeFailureReason reason) {
        return switch (reason) {
            case REJECTED -> FailureReason.REJECTED;
            case CONFIRM_TIMEOUT -> FailureReason.CONFIRM_TIMEOUT;
            case OUTBOX_PUBLISH_FAILURE -> FailureReason.OUTBOX_PUBLISH_FAILURE;
            case CANCELLED_BY_USER -> FailureReason.CANCELLED_BY_USER;
        };
    }
}
