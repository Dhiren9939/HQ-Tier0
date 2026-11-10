package me.dhiren9939.backend.access.application.usecase;

import me.dhiren9939.backend.access.application.port.in.DeleteApiKeyCommand;
import me.dhiren9939.backend.access.domain.ApiKey;
import me.dhiren9939.backend.access.domain.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.ApiKeyState;
import me.dhiren9939.backend.access.domain.ApiKeyUpdateFailure;
import me.dhiren9939.backend.access.domain.ChangeKind;
import me.dhiren9939.backend.shared.access.events.ApiKeyDeleted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeleteApiKeyHandlerTest {

    private InMemoryApiKeys apiKeys;
    private DeleteApiKeyHandler handler;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
        handler = new DeleteApiKeyHandler(apiKeys, CLOCK);
    }

    private void delete(UUID userId, ApiKey key) {
        handler.handle(new DeleteApiKeyCommand(userId, key.getApiKeyId()));
    }

    @Test
    void requestsDeletionAndRegistersOneDeletedEvent() {
        ApiKey key = apiKeys.existing(active());

        delete(USER_ID, key);

        assertThat(key.state()).isEqualTo(ApiKeyState.CHANGE_PENDING);
        assertThat(key.getPendingChange().getKind()).isEqualTo(ChangeKind.DELETE);
        assertThat(apiKeys.contains(key.getApiKeyId())).as("not removed until confirmed and dismissed").isTrue();
        assertThat(apiKeys.publishedEvents).hasSize(1);
        ApiKeyDeleted event = (ApiKeyDeleted) apiKeys.publishedEvents.getFirst();
        assertThat(event.version()).isEqualTo(2);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.emittedAt()).isNull();
    }

    @Test
    void aProvisioningKeyCannotBeDeleted() {
        ApiKey key = apiKeys.existing(provisioning());

        assertThatThrownBy(() -> delete(USER_ID, key)).isInstanceOf(ApiKeyNotFoundException.class);
        assertThat(apiKeys.publishedEvents).isEmpty();
    }

    @Test
    void pendingFailedAndDeletedKeysCannotBeDeleted() {
        ApiKey pending = apiKeys.existing(changePending());
        ApiKey failed = apiKeys.existing(changeFailed());
        ApiKey tombstone = apiKeys.existing(deleted());

        assertThatThrownBy(() -> delete(USER_ID, pending)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> delete(USER_ID, failed)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> delete(USER_ID, tombstone)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
        assertThat(apiKeys.publishedEvents).isEmpty();
    }

    @Test
    void unknownKeyAndForeignKeyAreNotFound() {
        ApiKey key = apiKeys.existing(active());

        assertThatThrownBy(() -> handler.handle(new DeleteApiKeyCommand(USER_ID, UUID.randomUUID())))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThatThrownBy(() -> delete(OTHER_USER_ID, key)).isInstanceOf(ApiKeyNotFoundException.class);
        assertThat(apiKeys.publishedEvents).isEmpty();
    }
}
