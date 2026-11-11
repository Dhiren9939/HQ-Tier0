package me.dhiren9939.backend.access.application.usecase.service.confirm;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeConfirmed;

import me.dhiren9939.backend.access.application.usecase.Fixtures;
import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeRejected;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;
import me.dhiren9939.backend.shared.access.events.outbound.DeleteApiKeyProjectionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmChangeHandlerTest {

    private InMemoryApiKeys apiKeys;
    private ConfirmChangeHandler handler;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
        handler = new ConfirmChangeHandler(apiKeys, CLOCK, transactions());
    }

    private void confirm(ApiKey key, long revision) {
        handler.handle(new ApiKeyChangeConfirmed(key.getApiKeyId(), revision, "data-plane"));
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
        assertThatThrownBy(() -> handler.handle(new ApiKeyChangeConfirmed(UUID.randomUUID(), 1, "data-plane")))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test
    void exhaustedRetriesBecomeAnUpdateFailure() {
        handler = new ConfirmChangeHandler(apiKeys, CLOCK, transactions(alwaysConflicts()));
        ApiKey key = apiKeys.existing(changePending());

        assertThatThrownBy(() -> confirm(key, 2)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
    }
}
