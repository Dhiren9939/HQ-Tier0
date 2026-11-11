package me.dhiren9939.backend.access.application.usecase.update;

import me.dhiren9939.backend.access.application.usecase.Fixtures;
import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;

import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;
import java.util.Map;
import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.domain.aggregates.apikey.PermissionChanges;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.events.outbound.UpdateApiKeyProjectionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateApiKeyHandlerTest {

    private InMemoryApiKeys apiKeys;
    private UpdateApiKeyHandler handler;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
        handler = new UpdateApiKeyHandler(apiKeys, CLOCK, transactions());
    }

    private ApiKeyView update(ApiKey key, String name, Lifetime lifetime, PermissionChanges permissions) {
        return handler.handle(new UpdateApiKeyCommand(USER_ID, key.getApiKeyId(), name, lifetime, permissions));
    }

    @Test
    void nameChangeCreatesAPendingUpdateAndOneEvent() {
        ApiKey key = apiKeys.existing(active());

        ApiKeyView view = update(key, "new-name", null, null);

        assertThat(view.state()).isEqualTo(ApiKeyState.CHANGE_PENDING);
        assertThat(view.apiKeyName()).as("confirmed name stays until the change is confirmed").isEqualTo(NAME);
        assertThat(apiKeys.saves).isEqualTo(1);
        assertThat(apiKeys.publishedEvents).hasSize(1);
        UpdateApiKeyProjectionCommand event = (UpdateApiKeyProjectionCommand) apiKeys.publishedEvents.getFirst();
        assertThat(event.version()).isEqualTo(2);
        assertThat(event.apiKeyName()).isEqualTo("new-name");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.emittedAt()).isNull();
    }

    @Test
    void permissionPatchOnlyTouchesTheGivenLevels() {
        ApiKey key = apiKeys.existing(active());

        update(key, null, null, new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE)));

        UpdateApiKeyProjectionCommand event = (UpdateApiKeyProjectionCommand) apiKeys.publishedEvents.getFirst();
        assertThat(event.permissions()).isEqualTo(Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE));
    }

    @Test
    void lifetimeChangeRestartsTheExpiryFromNow() {
        ApiKey key = apiKeys.existing(active());

        update(key, null, Lifetime.ONE_DAY, null);

        assertThat(apiKeys.publishedEvents).hasSize(1);
        assertThat(key.desiredState().expiresAt()).isEqualTo(NOW.plusSeconds(24 * 3600));
    }

    @Test
    void allFieldsAtOnceProduceASingleUpdate() {
        ApiKey key = apiKeys.existing(active());

        update(key, "all-new", Lifetime.NEVER,
                new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE, Scope.TENANT, AccessLevel.READ, Scope.CHANNEL, AccessLevel.READ_WRITE)));

        assertThat(apiKeys.publishedEvents).hasSize(1);
        UpdateApiKeyProjectionCommand event = (UpdateApiKeyProjectionCommand) apiKeys.publishedEvents.getFirst();
        assertThat(event.apiKeyName()).isEqualTo("all-new");
        assertThat(key.desiredState().expiresAt()).isNull();
        assertThat(event.version()).isEqualTo(2);
    }

    @Test
    void noopSameValueDoesNothing() {
        ApiKey key = apiKeys.existing(active());

        ApiKeyView view = update(key, NAME, null, null);

        assertThat(view.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(apiKeys.saves).isZero();
        assertThat(apiKeys.publishedEvents).isEmpty();
    }

    @Test
    void emptyRequestSamePermissionsAndSameExpiryAreAllNoOps() {
        ApiKey key = apiKeys.existing(active());

        update(key, null, null, null);
        update(key, null, null, new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.NONE)));
        update(key, null, Lifetime.ONE_MONTH, null);

        assertThat(apiKeys.saves).isZero();
        assertThat(apiKeys.publishedEvents).isEmpty();
    }

    @Test
    void aProvisioningKeyCannotBeUpdated() {
        ApiKey key = apiKeys.existing(provisioning());

        assertThatThrownBy(() -> update(key, "x", null, null)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
    }

    @Test
    void aKeyWithAPendingOrFailedChangeCannotBeUpdatedUntilDismissed() {
        ApiKey pending = apiKeys.existing(changePending());
        ApiKey failed = apiKeys.existing(changeFailed());

        assertThatThrownBy(() -> update(pending, "x", null, null)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> update(failed, "x", null, null)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
        assertThat(apiKeys.publishedEvents).isEmpty();
    }

    @Test
    void aDeletedKeyCannotBeUpdated() {
        ApiKey key = apiKeys.existing(deleted());

        assertThatThrownBy(() -> update(key, "x", null, null)).isInstanceOf(ApiKeyUpdateFailure.class);
    }

    @Test
    void unknownKeyAndForeignKeyAreNotFound() {
        ApiKey key = apiKeys.existing(active());

        assertThatThrownBy(() -> handler.handle(
                new UpdateApiKeyCommand(USER_ID, UUID.randomUUID(), "x", null, null)))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThatThrownBy(() -> handler.handle(
                new UpdateApiKeyCommand(OTHER_USER_ID, key.getApiKeyId(), "x", null, null)))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test
    void exhaustedOptimisticLockRetriesBecomeAnUpdateFailure() {
        handler = new UpdateApiKeyHandler(apiKeys, CLOCK, transactions(alwaysConflicts()));
        ApiKey key = apiKeys.existing(active());

        assertThatThrownBy(() -> update(key, "x", null, null)).isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(apiKeys.saves).isZero();
        assertThat(apiKeys.publishedEvents).isEmpty();
    }
}
