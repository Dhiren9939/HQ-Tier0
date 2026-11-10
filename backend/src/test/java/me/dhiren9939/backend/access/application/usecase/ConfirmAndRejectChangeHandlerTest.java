package me.dhiren9939.backend.access.application.usecase;

import me.dhiren9939.backend.access.application.port.in.ConfirmChangeCommand;
import me.dhiren9939.backend.access.application.port.in.RejectChangeCommand;
import me.dhiren9939.backend.access.domain.ApiKey;
import me.dhiren9939.backend.access.domain.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.ApiKeyState;
import me.dhiren9939.backend.access.domain.ApiKeyUpdateFailure;
import me.dhiren9939.backend.access.domain.ChangeStatus;
import me.dhiren9939.backend.shared.access.events.domain.FailureReason;
import me.dhiren9939.backend.shared.access.events.domain.DeleteApiKeyProjectionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmAndRejectChangeHandlerTest {

    private InMemoryApiKeys apiKeys;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
    }

    @Nested
    class Confirm {
        private ConfirmChangeHandler handler;

        @BeforeEach
        void setUp() {
            handler = new ConfirmChangeHandler(apiKeys, CLOCK, transactions());
        }

        private void confirm(ApiKey key, long revision) {
            handler.handle(new ConfirmChangeCommand(key.getApiKeyId(), revision));
        }

        @Test
        void confirmingTheCreationActivatesTheKey() {
            ApiKey key = apiKeys.existing(provisioning());

            confirm(key, 1);

            assertThat(key.state()).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(key.canAuthenticate(NOW)).isTrue();
            assertThat(apiKeys.saves).isEqualTo(1);
            assertThat(apiKeys.publishedEvents).as("confirming never emits an event").isEmpty();
        }

        @Test
        void confirmingTheDeletionLeavesAVisibleTombstone() {
            ApiKey key = apiKeys.existing(changePending());

            confirm(key, 2);

            assertThat(apiKeys.contains(key.getApiKeyId())).isTrue();
            assertThat(key.state()).isEqualTo(ApiKeyState.DELETED);
            assertThat(key.canAuthenticate(NOW)).isFalse();
        }

        @Test
        void aStaleRevisionIsRefused() {
            ApiKey key = apiKeys.existing(changePending());

            assertThatThrownBy(() -> confirm(key, 1)).isInstanceOf(ApiKeyUpdateFailure.class);
            assertThatThrownBy(() -> confirm(key, 3)).isInstanceOf(ApiKeyUpdateFailure.class);
            assertThat(key.state()).isEqualTo(ApiKeyState.CHANGE_PENDING);
            assertThat(apiKeys.saves).isZero();
        }

        @Test
        void nothingPendingIsRefused() {
            ApiKey key = apiKeys.existing(active());

            assertThatThrownBy(() -> confirm(key, 1)).isInstanceOf(ApiKeyUpdateFailure.class);
        }

        @Test
        void unknownKeyIsNotFound() {
            assertThatThrownBy(() -> handler.handle(new ConfirmChangeCommand(UUID.randomUUID(), 1)))
                    .isInstanceOf(ApiKeyNotFoundException.class);
        }
    }

    @Nested
    class Reject {
        private RejectChangeHandler handler;

        @BeforeEach
        void setUp() {
            handler = new RejectChangeHandler(apiKeys, CLOCK, transactions());
        }

        private void reject(ApiKey key, long revision, FailureReason reason) {
            handler.handle(new RejectChangeCommand(key.getApiKeyId(), revision, reason));
        }

        @Test
        void rejectingTheCreationFailsProvisioning() {
            ApiKey key = apiKeys.existing(provisioning());

            reject(key, 1, FailureReason.REJECTED);

            assertThat(key.state()).isEqualTo(ApiKeyState.PROVISIONING_FAILED);
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

                assertThat(key.state()).isEqualTo(ApiKeyState.CHANGE_FAILED);
                assertThat(key.getPendingChange().getFailureReason()).isEqualTo(reason);
            }
        }

        @Test
        void aStaleRevisionIsRefused() {
            ApiKey key = apiKeys.existing(changePending());

            assertThatThrownBy(() -> reject(key, 1, FailureReason.REJECTED)).isInstanceOf(ApiKeyUpdateFailure.class);
            assertThat(key.state()).isEqualTo(ApiKeyState.CHANGE_PENDING);
            assertThat(apiKeys.saves).isZero();
        }

        @Test
        void aMissingReasonIsRefused() {
            ApiKey key = apiKeys.existing(changePending());

            assertThatThrownBy(() -> reject(key, 2, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void unknownKeyIsNotFound() {
            assertThatThrownBy(() -> handler.handle(new RejectChangeCommand(UUID.randomUUID(), 1, FailureReason.REJECTED)))
                    .isInstanceOf(ApiKeyNotFoundException.class);
        }
    }

    @Test
    void confirmAndRejectTurnExhaustedRetriesIntoAnUpdateFailure() {
        ApiKey key = apiKeys.existing(changePending());

        assertThatThrownBy(() -> new ConfirmChangeHandler(apiKeys, CLOCK, transactions(alwaysConflicts()))
                .handle(new ConfirmChangeCommand(key.getApiKeyId(), 2))).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> new RejectChangeHandler(apiKeys, CLOCK, transactions(alwaysConflicts()))
                .handle(new RejectChangeCommand(key.getApiKeyId(), 2, FailureReason.REJECTED))).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
    }
}
