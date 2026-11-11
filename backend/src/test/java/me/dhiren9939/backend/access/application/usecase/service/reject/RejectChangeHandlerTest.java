package me.dhiren9939.backend.access.application.usecase.service.reject;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeRejected;

import me.dhiren9939.backend.access.application.usecase.Fixtures;
import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeConfirmed;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ChangeStatus;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;
import me.dhiren9939.backend.shared.access.events.outbound.DeleteApiKeyProjectionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RejectChangeHandlerTest {

    private InMemoryApiKeys apiKeys;
    private RejectChangeHandler handler;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
        handler = new RejectChangeHandler(apiKeys, CLOCK, transactions());
    }

    private void reject(ApiKey key, long revision, FailureReason reason) {
        handler.handle(new ApiKeyChangeRejected(key.getApiKeyId(), revision, "data-plane", reason));
    }

    @Test
    void rejectingTheCreationFailsProvisioning() {
        ApiKey key = apiKeys.existing(provisioning());

        reject(key, 1, FailureReason.REJECTED);

        assertThat(key.state(NOW)).isEqualTo(ApiKeyState.PROVISIONING_FAILED);
        assertThat(key.getPendingChange().getStatus()).isEqualTo(ChangeStatus.FAILED);
        assertThat(key.getPendingChange().getFailureReason()).isEqualTo(FailureReason.REJECTED);
        assertThat(apiKeys.saves).isEqualTo(1);
        assertThat(apiKeys.publishedEvents).hasSize(1);
        DeleteApiKeyProjectionCommand compensation = (DeleteApiKeyProjectionCommand) apiKeys.publishedEvents.getFirst();
        assertThat(compensation.version()).isEqualTo(2);
    }

    @Test
    void everyFailureReasonIsRecorded() {
        for (FailureReason reason : FailureReason.values()) {
            ApiKey key = apiKeys.existing(changePending());

            reject(key, 2, reason);

            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_FAILED);
            assertThat(key.getPendingChange().getFailureReason()).isEqualTo(reason);
        }
    }

    @Test
    void aStaleRevisionIsRefused() {
        ApiKey key = apiKeys.existing(changePending());

        assertThatThrownBy(() -> reject(key, 1, FailureReason.REJECTED)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_PENDING);
        assertThat(apiKeys.saves).isZero();
    }

    @Test
    void aMissingReasonIsRefused() {
        ApiKey key = apiKeys.existing(changePending());

        assertThatThrownBy(() -> reject(key, 2, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void unknownKeyIsNotFound() {
        assertThatThrownBy(() -> handler.handle(new ApiKeyChangeRejected(UUID.randomUUID(), 1, "data-plane", FailureReason.REJECTED)))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test
    void exhaustedRetriesBecomeAnUpdateFailure() {
        handler = new RejectChangeHandler(apiKeys, CLOCK, transactions(alwaysConflicts()));
        ApiKey key = apiKeys.existing(changePending());

        assertThatThrownBy(() -> reject(key, 2, FailureReason.REJECTED)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
    }
}
