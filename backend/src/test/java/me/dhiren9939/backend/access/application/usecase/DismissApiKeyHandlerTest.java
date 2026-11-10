package me.dhiren9939.backend.access.application.usecase;

import me.dhiren9939.backend.access.application.port.in.DismissApiKeyCommand;
import me.dhiren9939.backend.access.domain.ApiKey;
import me.dhiren9939.backend.access.domain.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.ApiKeyState;
import me.dhiren9939.backend.access.domain.ApiKeyUpdateFailure;
import me.dhiren9939.backend.shared.access.events.ApiKeyDeleted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DismissApiKeyHandlerTest {

    private InMemoryApiKeys apiKeys;
    private DismissApiKeyHandler handler;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
        handler = new DismissApiKeyHandler(apiKeys, CLOCK);
    }

    private void dismiss(UUID userId, ApiKey key) {
        handler.handle(new DismissApiKeyCommand(userId, key.getApiKeyId()));
    }

    @Test
    void dismissingAFailedChangeClearsItAndKeepsTheKeyWithoutAnEvent() {
        ApiKey key = apiKeys.existing(changeFailed());

        dismiss(USER_ID, key);

        assertThat(key.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(apiKeys.contains(key.getApiKeyId())).isTrue();
        assertThat(apiKeys.saves).isEqualTo(1);
        assertThat(apiKeys.publishedEvents).isEmpty();
    }

    @Test
    void dismissingAFailedCreationRemovesTheKeyAndEmitsADeleteAtTheNextRevision() {
        ApiKey key = apiKeys.existing(provisioningFailed());

        dismiss(USER_ID, key);

        assertThat(apiKeys.contains(key.getApiKeyId())).isFalse();
        assertThat(apiKeys.deletes).isEqualTo(1);
        assertThat(apiKeys.publishedEvents).hasSize(1);
        ApiKeyDeleted event = (ApiKeyDeleted) apiKeys.publishedEvents.getFirst();
        assertThat(event.version()).isEqualTo(2);
        assertThat(event.apiKeyId()).isEqualTo(key.getApiKeyId());
    }

    @Test
    void dismissingTheDeletedTombstoneRemovesTheKeyWithoutAnEvent() {
        ApiKey key = apiKeys.existing(deleted());

        dismiss(USER_ID, key);

        assertThat(apiKeys.contains(key.getApiKeyId())).isFalse();
        assertThat(apiKeys.deletes).isEqualTo(1);
        assertThat(apiKeys.publishedEvents).isEmpty();
    }

    @Test
    void keysWithNothingToDismissAreRefused() {
        ApiKey active = apiKeys.existing(active());
        ApiKey provisioning = apiKeys.existing(provisioning());
        ApiKey pending = apiKeys.existing(changePending());

        assertThatThrownBy(() -> dismiss(USER_ID, active)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> dismiss(USER_ID, provisioning)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> dismiss(USER_ID, pending)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
        assertThat(apiKeys.deletes).isZero();
        assertThat(apiKeys.publishedEvents).isEmpty();
    }

    @Test
    void unknownKeyAndForeignKeyAreNotFound() {
        ApiKey key = apiKeys.existing(deleted());

        assertThatThrownBy(() -> handler.handle(new DismissApiKeyCommand(USER_ID, UUID.randomUUID())))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThatThrownBy(() -> dismiss(OTHER_USER_ID, key)).isInstanceOf(ApiKeyNotFoundException.class);
        assertThat(apiKeys.contains(key.getApiKeyId())).isTrue();
    }
}
