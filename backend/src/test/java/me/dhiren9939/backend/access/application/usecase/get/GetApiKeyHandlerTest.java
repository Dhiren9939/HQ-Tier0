package me.dhiren9939.backend.access.application.usecase.get;

import me.dhiren9939.backend.access.application.usecase.Fixtures;
import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;

import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ChangeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetApiKeyHandlerTest {

    private InMemoryApiKeys apiKeys;
    private GetApiKeyHandler handler;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
        handler = new GetApiKeyHandler(apiKeys);
    }

    private ApiKeyView get(UUID userId, ApiKey key) {
        return handler.handle(new GetApiKeyQuery(userId, key.getApiKeyId()));
    }

    @Test
    void ownerSeesAnActiveKeyWithoutAnyPendingStatus() {
        ApiKey key = apiKeys.existing(active());

        ApiKeyView view = get(USER_ID, key);

        assertThat(view.apiKeyId()).isEqualTo(key.getApiKeyId());
        assertThat(view.accessId()).isEqualTo("V1_acc");
        assertThat(view.apiKeyName()).isEqualTo(NAME);
        assertThat(view.permissions()).isEqualTo(NO_PERMISSIONS);
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(view.expiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
        assertThat(view.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(view.updateStatus()).isNull();
    }

    @Test
    void ownerCanReadAProvisioningKey() {
        ApiKey key = apiKeys.existing(provisioning());

        ApiKeyView view = get(USER_ID, key);

        assertThat(view.state()).isEqualTo(ApiKeyState.PROVISIONING);
        assertThat(view.updateStatus()).isEqualTo(ChangeStatus.PENDING);
    }

    @Test
    void failedCreationAndFailedChangeShowTheirState() {
        ApiKey failedCreation = apiKeys.existing(provisioningFailed());
        assertThat(get(USER_ID, failedCreation).state()).isEqualTo(ApiKeyState.PROVISIONING_FAILED);
        assertThat(get(USER_ID, failedCreation).updateStatus()).isEqualTo(ChangeStatus.FAILED);

        ApiKey failedChange = apiKeys.existing(changeFailed());
        assertThat(get(USER_ID, failedChange).state()).isEqualTo(ApiKeyState.CHANGE_FAILED);
    }

    @Test
    void aDeletedKeyStaysVisibleAsATombstone() {
        ApiKey key = apiKeys.existing(deleted());

        assertThat(get(USER_ID, key).state()).isEqualTo(ApiKeyState.DELETED);
    }

    @Test
    void unknownKeyIsNotFound() {
        assertThatThrownBy(() -> handler.handle(new GetApiKeyQuery(USER_ID, UUID.randomUUID())))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test
    void anotherUsersKeyLooksIdenticalToAMissingOne() {
        ApiKey key = apiKeys.existing(active());

        assertThatThrownBy(() -> get(OTHER_USER_ID, key)).isInstanceOf(ApiKeyNotFoundException.class);
    }
}
