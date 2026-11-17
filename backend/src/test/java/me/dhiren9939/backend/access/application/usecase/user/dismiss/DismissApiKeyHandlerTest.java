package me.dhiren9939.backend.access.application.usecase.user.dismiss;

import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
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
        handler = new DismissApiKeyHandler(apiKeys, transactions());
    }

    private void dismiss(UUID userId, ApiKey key) {
        handler.handle(new DismissApiKeyCommand(userId, key.getApiKeyId()));
    }

    @Test
    void dismissingTheDeletedTombstoneRemovesTheKeyWithoutAnEvent() {
        ApiKey key = apiKeys.existing(deleted());

        dismiss(USER_ID, key);

        assertThat(apiKeys.contains(key.getApiKeyId())).isFalse();
        assertThat(apiKeys.deletes).isEqualTo(1);
        assertThat(apiKeys.publishedEvents).as("the delete went out when the key was deleted").isEmpty();
    }

    @Test
    void keysWithNothingToDismissAreRefused() {
        ApiKey active = apiKeys.existing(active());

        assertThatThrownBy(() -> dismiss(USER_ID, active)).isInstanceOf(ApiKeyUpdateFailure.class);
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

    @Test
    void exhaustedRetriesBecomeAnUpdateFailure() {
        ApiKey key = apiKeys.existing(deleted());
        handler = new DismissApiKeyHandler(apiKeys, transactions(alwaysConflicts()));

        assertThatThrownBy(() -> handler.handle(new DismissApiKeyCommand(USER_ID, key.getApiKeyId()))).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.deletes).isZero();
    }
}
