package me.dhiren9939.backend.access.application.usecase.cancel;

import me.dhiren9939.backend.access.application.usecase.Fixtures;
import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelApiKeyChangeHandlerTest {

    private InMemoryApiKeys apiKeys;
    private CancelApiKeyChangeHandler handler;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
        handler = new CancelApiKeyChangeHandler(apiKeys, CLOCK, transactions());
    }

    private void cancel(UUID userId, ApiKey key) {
        handler.handle(new CancelApiKeyChangeCommand(userId, key.getApiKeyId()));
    }

    @Test
    void cancellingAPendingUpdateFailsItAndEmitsTheCompensatingUpdate() {
        ApiKey key = apiKeys.existing(changePending());

        cancel(USER_ID, key);

        assertThat(key.state()).isEqualTo(ApiKeyState.CHANGE_FAILED);
        assertThat(key.getPendingChange().getFailureReason()).isEqualTo(FailureReason.CANCELLED_BY_USER);
        assertThat(apiKeys.saves).isEqualTo(1);
        assertThat(apiKeys.publishedEvents).hasSize(1);
    }

    @Test
    void cancellingAPendingCreationEmitsTheCompensatingDelete() {
        ApiKey key = apiKeys.existing(provisioning());

        cancel(USER_ID, key);

        assertThat(key.state()).isEqualTo(ApiKeyState.PROVISIONING_FAILED);
        assertThat(apiKeys.publishedEvents).hasSize(1);
    }

    @Test
    void aKeyWithoutAPendingChangeCannotBeCancelled() {
        ApiKey key = apiKeys.existing(active());

        assertThatThrownBy(() -> cancel(USER_ID, key)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
    }

    @Test
    void anAlreadyFailedChangeCannotBeCancelled() {
        ApiKey key = apiKeys.existing(changeFailed());

        assertThatThrownBy(() -> cancel(USER_ID, key)).isInstanceOf(ApiKeyUpdateFailure.class);
    }

    @Test
    void onlyTheOwnerCanCancel() {
        ApiKey key = apiKeys.existing(changePending());

        assertThatThrownBy(() -> cancel(OTHER_USER_ID, key)).isInstanceOf(ApiKeyNotFoundException.class);
        assertThat(key.state()).isEqualTo(ApiKeyState.CHANGE_PENDING);
    }

    @Test
    void exhaustedRetriesBecomeAnUpdateFailure() {
        handler = new CancelApiKeyChangeHandler(apiKeys, CLOCK, transactions(alwaysConflicts()));
        ApiKey key = apiKeys.existing(changePending());

        assertThatThrownBy(() -> cancel(USER_ID, key)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
    }
}
