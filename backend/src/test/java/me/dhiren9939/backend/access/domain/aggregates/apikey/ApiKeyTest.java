package me.dhiren9939.backend.access.domain.aggregates.apikey;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeRejected;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeConfirmed;

import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;

import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ChangeKind;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;
import java.util.Map;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.events.outbound.CreateApiKeyProjectionCommand;
import me.dhiren9939.backend.shared.access.events.outbound.DeleteApiKeyProjectionCommand;
import me.dhiren9939.backend.shared.access.events.outbound.ApiKeyProjectionCommand;
import me.dhiren9939.backend.shared.access.events.outbound.UpdateApiKeyProjectionCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests of the {@link ApiKey} aggregate: no Spring, no mocks. */
class ApiKeyTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-01-15T12:05:00Z");
    private static final Instant ONE_MONTH_FROM_NOW = Instant.parse("2026-02-15T12:00:00Z");
    private static final Instant ONE_DAY_FROM_LATER = Instant.parse("2026-01-16T12:05:00Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final String NAME = "test-key";
    private static final Permissions NO_PERMISSIONS = Permissions.none();
    private static final IssuedCredential CREDENTIAL = new IssuedCredential("V1_acc", "secret_raw", "hashed_secret");

    private static List<ApiKeyProjectionCommand> commands(List<ApiKeyProjectionCommand> events) {
        return events;
    }

    private static ApiKey provisioning() {
        return ApiKey.issue(USER_ID, NAME, CREDENTIAL, NO_PERMISSIONS, NOW, ONE_MONTH_FROM_NOW);
    }

    private static ApiKey active() {
        ApiKey key = provisioning();
        key.confirm(1, NOW);
        return key;
    }

    private static ApiKey withPendingUpdate() {
        ApiKey key = active();
        key.requestUpdate(new ApiKeyChanges("renamed", null, null), NOW);
        return key;
    }

    private static ApiKey withFailedUpdate() {
        ApiKey key = withPendingUpdate();
        key.reject(2, FailureReason.REJECTED, NOW);
        return key;
    }

    private static ApiKey withPendingDelete() {
        ApiKey key = active();
        key.requestDeletion(NOW);
        return key;
    }

    private static ApiKey deleted() {
        ApiKey key = withPendingDelete();
        key.confirm(2, NOW);
        return key;
    }

    private static ApiKey provisioningFailed() {
        ApiKey key = provisioning();
        key.reject(1, FailureReason.CONFIRM_TIMEOUT, NOW);
        return key;
    }

    private static ApiKeyChanges name(String name) {
        return new ApiKeyChanges(name, null, null);
    }

    private static ApiKeyChanges permissions(PermissionChanges changes) {
        return new ApiKeyChanges(null, null, changes);
    }

    @Nested
    @DisplayName("issue()")
    class Issue {

        @Test
        @DisplayName("creates a provisioning key at revision 1 with a pending creation")
        void happyPath() {
            ApiKey key = provisioning();

            assertThat(key.getApiKeyId()).isNotNull();
            assertThat(key.getUserId()).isEqualTo(USER_ID);
            assertThat(key.getApiKeyName()).isEqualTo(NAME);
            assertThat(key.getAccessId()).isEqualTo("V1_acc");
            assertThat(key.getAccessSecretHash()).isEqualTo("hashed_secret");
            assertThat(key.getPermissions()).isEqualTo(NO_PERMISSIONS);
            assertThat(key.getCreatedAt()).isEqualTo(NOW);
            assertThat(key.getExpiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
            assertThat(key.getRevision()).isEqualTo(1);
            assertThat(key.getConfirmedRevision()).isZero();
            assertThat(key.getDeletedAt()).isNull();
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.PROVISIONING);

            PendingChange pending = key.getPendingChange();
            assertThat(pending.getKind()).isEqualTo(ChangeKind.CREATE);
            assertThat(pending.getStatus()).isEqualTo(ChangeStatus.PENDING);
            assertThat(pending.getRevision()).isEqualTo(1);
            assertThat(pending.getRequestedAt()).isEqualTo(NOW);
            assertThat(pending.getApiKeyName()).isEqualTo(NAME);
            assertThat(pending.getPermissions()).isEqualTo(NO_PERMISSIONS);
            assertThat(pending.getExpiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
        }

        @Test
        @DisplayName("preserves a null expiry for non-expiring keys")
        void nullExpiry() {
            ApiKey key = ApiKey.issue(USER_ID, NAME, CREDENTIAL, NO_PERMISSIONS, NOW, null);

            assertThat(key.getExpiresAt()).isNull();
            assertThat(key.getPendingChange().getExpiresAt()).isNull();
        }

        @Test
        @DisplayName("a provisioning key is readable by its owner but is not the data plane's yet")
        void provisioningKeyIsReadable() {
            ApiKey key = provisioning();

            assertThat(key.isOwnedBy(USER_ID)).isTrue();
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.PROVISIONING);
            assertThat(key.desiredState())
                    .isEqualTo(new DesiredState(NAME, NO_PERMISSIONS, ONE_MONTH_FROM_NOW));
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        void ownerOnly() {
            ApiKey key = active();

            assertThat(key.isOwnedBy(USER_ID)).isTrue();
            assertThat(key.isOwnedBy(OTHER_USER_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("canAuthenticate()")
    class CanAuthenticate {

        @Test
        void activeKey() {
            assertThat(active().canAuthenticate(NOW)).isTrue();
        }

        @Test
        void nonExpiringKey() {
            ApiKey key = ApiKey.issue(USER_ID, NAME, CREDENTIAL, NO_PERMISSIONS, NOW, null);
            key.confirm(1, NOW);

            assertThat(key.canAuthenticate(Instant.parse("2099-01-01T00:00:00Z"))).isTrue();
        }

        @Test
        void expiredKey() {
            assertThat(active().canAuthenticate(ONE_MONTH_FROM_NOW.plusSeconds(1))).isFalse();
        }

        @Test
        void provisioningKey() {
            assertThat(provisioning().canAuthenticate(NOW)).isFalse();
        }

        @Test
        void provisioningFailedKey() {
            assertThat(provisioningFailed().canAuthenticate(NOW)).isFalse();
        }

        @Test
        void deletedKey() {
            assertThat(deleted().canAuthenticate(NOW)).isFalse();
        }

        @Test
        @DisplayName("a key with a pending update still authenticates, with its confirmed attributes")
        void pendingUpdateKeepsOldState() {
            ApiKey key = withPendingUpdate();

            assertThat(key.canAuthenticate(NOW)).isTrue();
            assertThat(key.getApiKeyName()).isEqualTo(NAME);
        }

        @Test
        @DisplayName("a key with a pending delete still authenticates until the delete is confirmed")
        void pendingDeleteStillAuthenticates() {
            assertThat(withPendingDelete().canAuthenticate(NOW)).isTrue();
        }
    }

    @Nested
    @DisplayName("state(now) and expiry")
    class ExpiredState {
        private final Instant afterExpiry = ONE_MONTH_FROM_NOW.plusSeconds(1);

        @Test
        void aConfirmedKeyPastItsExpiryIsExpired() {
            assertThat(active().state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(active().state(ONE_MONTH_FROM_NOW)).as("the expiry instant itself is still valid").isEqualTo(ApiKeyState.ACTIVE);
            assertThat(active().state(afterExpiry)).isEqualTo(ApiKeyState.EXPIRED);
        }

        @Test
        void aKeyThatNeverExpiresIsNeverExpired() {
            ApiKey key = ApiKey.issue(USER_ID, NAME, CREDENTIAL, NO_PERMISSIONS, NOW, null);
            key.confirm(1, NOW);

            assertThat(key.state(Instant.parse("2099-01-01T00:00:00Z"))).isEqualTo(ApiKeyState.ACTIVE);
        }

        @Test
        @DisplayName("expiry takes precedence over a pending or failed change on a confirmed key")
        void expiredBeatsPendingAndFailedChanges() {
            assertThat(withPendingUpdate().state(afterExpiry)).isEqualTo(ApiKeyState.EXPIRED);
            assertThat(withPendingDelete().state(afterExpiry)).isEqualTo(ApiKeyState.EXPIRED);
            assertThat(withPendingUpdate().state(NOW)).isEqualTo(ApiKeyState.CHANGE_PENDING);
        }

        @Test
        void keysThatWereNeverConfirmedOrAreDeletedKeepTheirOwnState() {
            assertThat(provisioning().state(afterExpiry)).isEqualTo(ApiKeyState.PROVISIONING);
            assertThat(provisioningFailed().state(afterExpiry)).isEqualTo(ApiKeyState.PROVISIONING_FAILED);
            assertThat(deleted().state(afterExpiry)).isEqualTo(ApiKeyState.DELETED);
        }

        @Test
        void anExpiredKeyCannotAuthenticateButCanStillBeRenewedOrDeleted() {
            ApiKey key = active();

            assertThat(key.canAuthenticate(afterExpiry)).isFalse();
            assertThat(key.requestUpdate(new ApiKeyChanges(null, Lifetime.ONE_MONTH, null), afterExpiry)).isTrue();
        }
    }

    @Nested
    @DisplayName("requestUpdate()")
    class RequestUpdate {

        @Test
        @DisplayName("updates name only")
        void nameOnly() {
            ApiKey key = active();

            boolean changed = key.requestUpdate(name("new-name"), LATER);

            assertThat(changed).isTrue();
            assertThat(key.getRevision()).isEqualTo(2);
            assertThat(key.getApiKeyName()).isEqualTo(NAME);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_PENDING);

            PendingChange pending = key.getPendingChange();
            assertThat(pending.getKind()).isEqualTo(ChangeKind.UPDATE);
            assertThat(pending.getStatus()).isEqualTo(ChangeStatus.PENDING);
            assertThat(pending.getRevision()).isEqualTo(2);
            assertThat(pending.getRequestedAt()).isEqualTo(LATER);
            assertThat(pending.getApiKeyName()).isEqualTo("new-name");
            assertThat(pending.getPermissions()).isEqualTo(NO_PERMISSIONS);
            assertThat(pending.getExpiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
        }

        @Test
        @DisplayName("updates the lifetime, restarting it from now")
        void lifetime() {
            ApiKey key = active();

            boolean changed = key.requestUpdate(new ApiKeyChanges(null, Lifetime.ONE_DAY, null), LATER);

            assertThat(changed).isTrue();
            assertThat(key.getPendingChange().getExpiresAt()).isEqualTo(ONE_DAY_FROM_LATER);
            assertThat(key.getExpiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
        }

        @Test
        @DisplayName("lifetime NEVER clears the expiry")
        void neverExpires() {
            ApiKey key = active();

            boolean changed = key.requestUpdate(new ApiKeyChanges(null, Lifetime.NEVER, null), NOW);

            assertThat(changed).isTrue();
            assertThat(key.getPendingChange().getExpiresAt()).isNull();
        }

        @Test
        @DisplayName("updates ingestion permission only")
        void ingestionPermission() {
            ApiKey key = active();

            key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE))), NOW);

            assertThat(key.getPendingChange().getPermissions()).isEqualTo(Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE));
            assertThat(key.getPermissions()).isEqualTo(NO_PERMISSIONS);
        }

        @Test
        @DisplayName("updates tenant permission only")
        void tenantPermission() {
            ApiKey key = active();

            key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ_WRITE))), NOW);

            assertThat(key.getPendingChange().getPermissions()).isEqualTo(Permissions.none().with(Scope.INGESTION, AccessLevel.NONE).with(Scope.TENANT, AccessLevel.READ_WRITE).with(Scope.CHANNEL, AccessLevel.NONE));
        }

        @Test
        @DisplayName("updates channel permission only")
        void channelPermission() {
            ApiKey key = active();

            key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.CHANNEL, AccessLevel.READ_WRITE))), NOW);

            assertThat(key.getPendingChange().getPermissions()).isEqualTo(Permissions.none().with(Scope.INGESTION, AccessLevel.NONE).with(Scope.TENANT, AccessLevel.NONE).with(Scope.CHANNEL, AccessLevel.READ_WRITE));
        }

        @Test
        @DisplayName("updates all fields at once")
        void allFields() {
            ApiKey key = active();
            Permissions expected = Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE).with(Scope.TENANT, AccessLevel.READ).with(Scope.CHANNEL, AccessLevel.WRITE);

            key.requestUpdate(new ApiKeyChanges("all-new", Lifetime.ONE_DAY, new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE, Scope.TENANT, AccessLevel.READ, Scope.CHANNEL, AccessLevel.WRITE))), LATER);

            assertThat(key.getRevision()).isEqualTo(2);
            assertThat(key.getPendingChange().getApiKeyName()).isEqualTo("all-new");
            assertThat(key.getPendingChange().getPermissions()).isEqualTo(expected);
            assertThat(key.getPendingChange().getExpiresAt()).isEqualTo(ONE_DAY_FROM_LATER);
        }

        @Test
        @DisplayName("no-op when nothing is requested")
        void noOpAllNull() {
            ApiKey key = active();

            assertThat(key.requestUpdate(new ApiKeyChanges(null, null, null), NOW)).isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
            assertThat(key.getPendingChange()).isNull();
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
        }

        @Test
        @DisplayName("no-op when the name is the same")
        void noOpSameName() {
            ApiKey key = active();

            assertThat(key.requestUpdate(name(NAME), NOW)).isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
            assertThat(key.getPendingChange()).isNull();
        }

        @Test
        @DisplayName("no-op when every permission level is the same")
        void noOpSamePermissions() {
            ApiKey key = active();

            assertThat(key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.NONE, Scope.TENANT, AccessLevel.NONE, Scope.CHANNEL, AccessLevel.NONE))), NOW))
                    .isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
            assertThat(key.getPendingChange()).isNull();
        }

        @Test
        @DisplayName("no-op when the permission changes are empty")
        void noOpEmptyPermissionChanges() {
            ApiKey key = active();
            key.pullProjectionCommands();

            assertThat(key.requestUpdate(permissions(PermissionChanges.none()), NOW)).isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
            assertThat(key.getPendingChange()).isNull();
            assertThat(key.pullProjectionCommands()).isEmpty();
        }

        @Test
        @DisplayName("no-op when a granted level is requested again")
        void noOpSameGrantedLevel() {
            ApiKey key = active();
            key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ))), NOW);
            key.confirm(2, NOW);

            assertThat(key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ))), LATER))
                    .isFalse();

            assertThat(key.getRevision()).isEqualTo(2);
        }

        @Test
        @DisplayName("setting a scope to NONE revokes it once confirmed")
        void revokingAScope() {
            ApiKey key = active();
            key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ_WRITE))), NOW);
            key.confirm(2, NOW);
            assertThat(key.getPermissions().levelOf(Scope.TENANT)).isEqualTo(AccessLevel.READ_WRITE);

            assertThat(key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.NONE))), LATER))
                    .isTrue();
            // still the confirmed permissions until the data plane confirms
            assertThat(key.getPermissions().levelOf(Scope.TENANT)).isEqualTo(AccessLevel.READ_WRITE);
            key.confirm(3, LATER);

            assertThat(key.getPermissions()).isEqualTo(Permissions.none());
        }

        @Test
        @DisplayName("an unrelated scope keeps its level when another one changes")
        void otherScopesAreUntouched() {
            ApiKey key = active();
            key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE))), NOW);
            key.confirm(2, NOW);

            key.requestUpdate(permissions(new PermissionChanges(Map.of(Scope.CHANNEL, AccessLevel.READ))), LATER);
            key.confirm(3, LATER);

            assertThat(key.getPermissions()).isEqualTo(Permissions.none()
                    .with(Scope.INGESTION, AccessLevel.WRITE).with(Scope.CHANNEL, AccessLevel.READ));
        }

        @Test
        @DisplayName("no-op when the computed expiry equals the current expiry")
        void noOpSameExpiry() {
            ApiKey key = active();

            assertThat(key.requestUpdate(new ApiKeyChanges(null, Lifetime.ONE_MONTH, null), NOW)).isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
            assertThat(key.getPendingChange()).isNull();
        }

        @Test
        @DisplayName("refused while creation is pending")
        void refusedWhileProvisioning() {
            ApiKey key = provisioning();

            assertThatThrownBy(() -> key.requestUpdate(name("x"), NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("Can't update an API key with pending creation");
            assertThat(key.getRevision()).isEqualTo(1);
        }

        @Test
        @DisplayName("refused while a change is pending")
        void refusedWhileChangePending() {
            ApiKey key = withPendingUpdate();

            assertThatThrownBy(() -> key.requestUpdate(name("x"), NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("Can't update an API key with pending changes");
            assertThat(key.getRevision()).isEqualTo(2);
        }

        @Test
        @DisplayName("refused while a failed change is waiting to be dismissed")
        void refusedWhileChangeFailed() {
            ApiKey key = withFailedUpdate();

            assertThatThrownBy(() -> key.requestUpdate(name("x"), NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessageContaining("dismiss it first");
        }

        @Test
        @DisplayName("refused after creation failed")
        void refusedAfterProvisioningFailed() {
            ApiKey key = provisioningFailed();

            assertThatThrownBy(() -> key.requestUpdate(name("x"), NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessageContaining("dismiss it first");
        }

        @Test
        @DisplayName("refused on a deleted key")
        void refusedWhenDeleted() {
            ApiKey key = deleted();

            assertThatThrownBy(() -> key.requestUpdate(name("x"), NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("Can't update a deleted API key");
        }
    }

    @Nested
    @DisplayName("requestDeletion()")
    class RequestDeletion {

        @Test
        @DisplayName("creates a pending DELETE at the next revision with the current attributes")
        void happyPath() {
            ApiKey key = active();

            key.requestDeletion(LATER);

            assertThat(key.getRevision()).isEqualTo(2);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_PENDING);
            PendingChange pending = key.getPendingChange();
            assertThat(pending.getKind()).isEqualTo(ChangeKind.DELETE);
            assertThat(pending.getStatus()).isEqualTo(ChangeStatus.PENDING);
            assertThat(pending.getRevision()).isEqualTo(2);
            assertThat(pending.getRequestedAt()).isEqualTo(LATER);
            assertThat(pending.getApiKeyName()).isEqualTo(NAME);
            assertThat(pending.getPermissions()).isEqualTo(NO_PERMISSIONS);
            assertThat(pending.getExpiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
            assertThat(key.getDeletedAt()).isNull();
        }

        @Test
        void refusedWhileProvisioning() {
            ApiKey key = provisioning();

            assertThatThrownBy(() -> key.requestDeletion(NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("Can't delete an API key with pending creation");
        }

        @Test
        void refusedWhileChangePending() {
            ApiKey key = withPendingUpdate();

            assertThatThrownBy(() -> key.requestDeletion(NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("Can't delete an API key with pending changes");
        }

        @Test
        void refusedWhileChangeFailed() {
            ApiKey key = withFailedUpdate();

            assertThatThrownBy(() -> key.requestDeletion(NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessageContaining("dismiss it first");
        }

        @Test
        void refusedWhenAlreadyDeleted() {
            ApiKey key = deleted();

            assertThatThrownBy(() -> key.requestDeletion(NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class)
                    .hasMessage("Can't delete a deleted API key");
        }
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("a creation makes the key active")
        void creation() {
            ApiKey key = provisioning();

            key.confirm(1, LATER);

            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(key.getConfirmedRevision()).isEqualTo(1);
            assertThat(key.getPendingChange()).isNull();
            assertThat(key.canAuthenticate(LATER)).isTrue();
        }

        @Test
        @DisplayName("an update applies the pending attributes")
        void update() {
            ApiKey key = active();
            key.requestUpdate(new ApiKeyChanges("new-name", Lifetime.NEVER,
                    new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE))), NOW);

            key.confirm(2, LATER);

            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(key.getConfirmedRevision()).isEqualTo(2);
            assertThat(key.getApiKeyName()).isEqualTo("new-name");
            assertThat(key.getExpiresAt()).isNull();
            assertThat(key.getPermissions().levelOf(Scope.INGESTION)).isEqualTo(AccessLevel.WRITE);
            assertThat(key.getPendingChange()).isNull();
        }

        @Test
        @DisplayName("a delete leaves a tombstone: the key stays but is DELETED and cannot authenticate")
        void deletion() {
            ApiKey key = withPendingDelete();

            key.confirm(2, LATER);

            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.DELETED);
            assertThat(key.getDeletedAt()).isEqualTo(LATER);
            assertThat(key.getConfirmedRevision()).isEqualTo(2);
            assertThat(key.getPendingChange()).isNull();
            assertThat(key.canAuthenticate(LATER)).isFalse();
            assertThat(key.isOwnedBy(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("refuses a revision that is not the pending one")
        void staleRevision() {
            ApiKey key = withPendingUpdate();

            assertThatThrownBy(() -> key.confirm(1, NOW)).isInstanceOf(ApiKeyUpdateFailure.class);
            assertThatThrownBy(() -> key.confirm(3, NOW)).isInstanceOf(ApiKeyUpdateFailure.class);

            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_PENDING);
            assertThat(key.getApiKeyName()).isEqualTo(NAME);
            assertThat(key.getConfirmedRevision()).isEqualTo(1);
        }

        @Test
        void refusesWhenNothingIsPending() {
            ApiKey key = active();

            assertThatThrownBy(() -> key.confirm(1, NOW)).isInstanceOf(ApiKeyUpdateFailure.class);
        }

        @Test
        @DisplayName("refuses a change that already failed")
        void refusesFailedChange() {
            ApiKey key = withFailedUpdate();

            assertThatThrownBy(() -> key.confirm(2, NOW)).isInstanceOf(ApiKeyUpdateFailure.class);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_FAILED);
        }
    }

    @Nested
    @DisplayName("reject()")
    class Reject {

        @ParameterizedTest
        @EnumSource(FailureReason.class)
        @DisplayName("fails the pending change and records the reason")
        void recordsReason(FailureReason reason) {
            ApiKey key = withPendingUpdate();

            key.reject(2, reason, NOW);

            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_FAILED);
            assertThat(key.getPendingChange().getStatus()).isEqualTo(ChangeStatus.FAILED);
            assertThat(key.getPendingChange().getFailureReason()).isEqualTo(reason);
        }

        @Test
        void failedCreationIsProvisioningFailed() {
            ApiKey key = provisioning();

            key.reject(1, FailureReason.OUTBOX_PUBLISH_FAILURE, NOW);

            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.PROVISIONING_FAILED);
            assertThat(key.canAuthenticate(NOW)).isFalse();
        }

        @Test
        void refusesStaleRevision() {
            ApiKey key = withPendingUpdate();

            assertThatThrownBy(() -> key.reject(1, FailureReason.REJECTED, NOW)).isInstanceOf(ApiKeyUpdateFailure.class);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_PENDING);
        }

        @Test
        void refusesWhenNothingIsPending() {
            ApiKey key = active();

            assertThatThrownBy(() -> key.reject(1, FailureReason.REJECTED, NOW)).isInstanceOf(ApiKeyUpdateFailure.class);
        }

        @Test
        void refusesAnAlreadyFailedChange() {
            ApiKey key = withFailedUpdate();

            assertThatThrownBy(() -> key.reject(2, FailureReason.CONFIRM_TIMEOUT, NOW))
                    .isInstanceOf(ApiKeyUpdateFailure.class);
            assertThat(key.getPendingChange().getFailureReason()).isEqualTo(FailureReason.REJECTED);
        }

        @Test
        void requiresAReason() {
            ApiKey key = withPendingUpdate();

            assertThatThrownBy(() -> key.reject(2, null, NOW)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("dismiss()")
    class Dismiss {

        @Test
        @DisplayName("a failed update is cleared and the key is active again, with no event")
        void failedUpdate() {
            ApiKey key = withFailedUpdate();

            DismissResult result = key.dismiss();

            assertThat(result).isEqualTo(DismissResult.CLEARED);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(key.getPendingChange()).isNull();
            assertThat(key.getApiKeyName()).isEqualTo(NAME);
        }

        @Test
        @DisplayName("a failed delete is cleared and the key is active again")
        void failedDelete() {
            ApiKey key = withPendingDelete();
            key.reject(2, FailureReason.REJECTED, NOW);

            assertThat(key.dismiss()).isEqualTo(DismissResult.CLEARED);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(key.canAuthenticate(NOW)).isTrue();
        }

        @Test
        @DisplayName("after dismissing a failure, the next change gets a fresh revision")
        void changeAfterDismiss() {
            ApiKey key = withFailedUpdate();
            key.dismiss();

            assertThat(key.requestUpdate(name("again"), NOW)).isTrue();

            assertThat(key.getRevision()).as("revision 3 went to the compensating update").isEqualTo(4);
        }

        @Test
        @DisplayName("a failed creation is removed (its delete command went out when it failed)")
        void failedCreation() {
            ApiKey key = provisioningFailed();

            DismissResult result = key.dismiss();

            assertThat(result).isEqualTo(DismissResult.REMOVED);
            assertThat(key.getRevision()).isEqualTo(2);
            assertThat(key.desiredState()).isEqualTo(new DesiredState(NAME, NO_PERMISSIONS, ONE_MONTH_FROM_NOW));
        }

        @Test
        @DisplayName("a deleted tombstone is removed without an event")
        void tombstone() {
            ApiKey key = deleted();

            DismissResult result = key.dismiss();

            assertThat(result).isEqualTo(DismissResult.REMOVED);
            assertThat(key.getRevision()).isEqualTo(2);
        }

        @Test
        void nothingToDismissOnActiveKey() {
            ApiKey key = active();

            assertThatThrownBy(() -> key.dismiss()).isInstanceOf(ApiKeyUpdateFailure.class);
        }

        @Test
        void nothingToDismissWhileProvisioning() {
            ApiKey key = provisioning();

            assertThatThrownBy(() -> key.dismiss()).isInstanceOf(ApiKeyUpdateFailure.class);
        }

        @Test
        void nothingToDismissWhileChangePending() {
            ApiKey key = withPendingUpdate();

            assertThatThrownBy(() -> key.dismiss()).isInstanceOf(ApiKeyUpdateFailure.class);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_PENDING);
        }
    }

    @Nested
    @DisplayName("projection commands")
    class DomainEvents {

        private ApiKey activeWithoutEvents() {
            ApiKey key = active();
            key.pullProjectionCommands();
            return key;
        }

        @Test
        @DisplayName("issuing registers CreateApiKeyProjectionCommand at revision 1 with the secret hash, occurredAt = now and emittedAt null")
        void issueRegistersCreated() {
            ApiKey key = provisioning();

            List<ApiKeyProjectionCommand> events = key.pullProjectionCommands();

            assertThat(events).hasSize(1);
            CreateApiKeyProjectionCommand created = (CreateApiKeyProjectionCommand) events.getFirst();
            assertThat(created.eventType()).isEqualTo("apiKey.projection.create");
            assertThat(created.apiKeyId()).isEqualTo(key.getApiKeyId());
            assertThat(created.version()).isEqualTo(1);
            assertThat(created.occurredAt()).isEqualTo(NOW);
            assertThat(created.emittedAt()).isNull();
            assertThat(created.userId()).isEqualTo(USER_ID);
            assertThat(created.accessId()).isEqualTo("V1_acc");
            assertThat(created.accessSecretHash()).isEqualTo("hashed_secret");
            assertThat(created.apiKeyName()).isEqualTo(NAME);
            assertThat(created.permissions()).isEqualTo(NO_PERMISSIONS);
            assertThat(created.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("pulling hands the events over once")
        void pullClears() {
            ApiKey key = provisioning();

            assertThat(key.pullProjectionCommands()).hasSize(1);
            assertThat(key.pullProjectionCommands()).isEmpty();
        }

        @Test
        @DisplayName("an update registers UpdateApiKeyProjectionCommand with the pending state at the new revision")
        void updateRegistersUpdated() {
            ApiKey key = activeWithoutEvents();

            key.requestUpdate(new ApiKeyChanges("renamed", Lifetime.NEVER,
                    new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE))), LATER);

            UpdateApiKeyProjectionCommand updated = (UpdateApiKeyProjectionCommand) commands(key.pullProjectionCommands()).getFirst();
            assertThat(updated.eventType()).isEqualTo("apiKey.projection.update");
            assertThat(updated.version()).isEqualTo(2);
            assertThat(updated.occurredAt()).isEqualTo(LATER);
            assertThat(updated.emittedAt()).isNull();
            assertThat(updated.apiKeyName()).isEqualTo("renamed");
            assertThat(updated.permissions().levelOf(Scope.INGESTION)).isEqualTo(AccessLevel.WRITE);
            assertThat(updated.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("an update that changes nothing registers no event")
        void noOpRegistersNothing() {
            ApiKey key = activeWithoutEvents();

            assertThat(key.requestUpdate(name(NAME), LATER)).isFalse();

            assertThat(key.pullProjectionCommands()).isEmpty();
        }

        @Test
        @DisplayName("a refused change registers no event")
        void refusedChangeRegistersNothing() {
            ApiKey key = withPendingUpdate();
            key.pullProjectionCommands();

            assertThatThrownBy(() -> key.requestDeletion(LATER)).isInstanceOf(ApiKeyUpdateFailure.class);

            assertThat(key.pullProjectionCommands()).isEmpty();
        }

        @Test
        @DisplayName("requesting deletion registers DeleteApiKeyProjectionCommand at the new revision")
        void deletionRegistersDeleted() {
            ApiKey key = activeWithoutEvents();

            key.requestDeletion(LATER);

            DeleteApiKeyProjectionCommand deleted = (DeleteApiKeyProjectionCommand) commands(key.pullProjectionCommands()).getFirst();
            assertThat(deleted.eventType()).isEqualTo("apiKey.projection.delete");
            assertThat(deleted.version()).isEqualTo(2);
            assertThat(deleted.occurredAt()).isEqualTo(LATER);
            assertThat(deleted.emittedAt()).isNull();
            assertThat(deleted.apiKeyName()).isEqualTo(NAME);
            assertThat(deleted.accessId()).isEqualTo("V1_acc");
        }

        @Test
        @DisplayName("rejecting a creation registers a delete at the next revision, at once")
        void rejectedCreationRegistersDeleted() {
            ApiKey key = provisioning();
            key.pullProjectionCommands();

            key.reject(1, FailureReason.REJECTED, LATER);

            DeleteApiKeyProjectionCommand deleted = (DeleteApiKeyProjectionCommand) commands(key.pullProjectionCommands()).getFirst();
            assertThat(deleted.version()).isEqualTo(2);
            assertThat(deleted.occurredAt()).isEqualTo(LATER);
            assertThat(deleted.emittedAt()).isNull();
            assertThat(deleted.permissions()).isEqualTo(NO_PERMISSIONS);
        }

        @Test
        @DisplayName("rejecting an update registers an update restating the confirmed state at the next revision")
        void rejectedUpdateRegistersUpdateOfConfirmedState() {
            ApiKey key = withPendingUpdate();
            key.pullProjectionCommands();

            key.reject(2, FailureReason.CONFIRM_TIMEOUT, LATER);

            List<ApiKeyProjectionCommand> events = commands(key.pullProjectionCommands());
            assertThat(events).hasSize(1);
            UpdateApiKeyProjectionCommand update = (UpdateApiKeyProjectionCommand) events.getFirst();
            assertThat(update.version()).isEqualTo(3);
            assertThat(update.apiKeyName()).as("the confirmed name, not the rejected one").isEqualTo(NAME);
            assertThat(update.occurredAt()).isEqualTo(LATER);
            assertThat(key.getRevision()).isEqualTo(3);
        }

        @Test
        @DisplayName("rejecting a delete registers a create that restores the key at the next revision")
        void rejectedDeleteRegistersCreate() {
            ApiKey key = withPendingDelete();
            key.pullProjectionCommands();

            key.reject(2, FailureReason.CONFIRM_TIMEOUT, LATER);

            List<ApiKeyProjectionCommand> events = commands(key.pullProjectionCommands());
            assertThat(events).hasSize(1);
            CreateApiKeyProjectionCommand create = (CreateApiKeyProjectionCommand) events.getFirst();
            assertThat(create.version()).isEqualTo(3);
            assertThat(create.apiKeyName()).isEqualTo(NAME);
            assertThat(create.accessSecretHash()).isNotNull();
            assertThat(create.occurredAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("cancelling a pending update fails it as CANCELLED_BY_USER and registers the compensating update")
        void cancelledUpdate() {
            ApiKey key = withPendingUpdate();
            key.pullProjectionCommands();

            key.cancelPendingChange(LATER);

            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.CHANGE_FAILED);
            assertThat(key.getPendingChange().getFailureReason()).isEqualTo(FailureReason.CANCELLED_BY_USER);
            UpdateApiKeyProjectionCommand update = (UpdateApiKeyProjectionCommand) commands(key.pullProjectionCommands()).getFirst();
            assertThat(update.version()).isEqualTo(3);
            assertThat(update.apiKeyName()).isEqualTo(NAME);
        }

        @Test
        @DisplayName("cancelling a pending delete registers a restoring create; cancelling a creation registers a delete")
        void cancelledDeleteAndCreation() {
            ApiKey pendingDelete = withPendingDelete();
            pendingDelete.pullProjectionCommands();
            ApiKey provisioning = provisioning();
            provisioning.pullProjectionCommands();

            pendingDelete.cancelPendingChange(LATER);
            provisioning.cancelPendingChange(LATER);

            assertThat(commands(pendingDelete.pullProjectionCommands()).getFirst()).isInstanceOf(CreateApiKeyProjectionCommand.class);
            assertThat(commands(provisioning.pullProjectionCommands()).getFirst()).isInstanceOf(DeleteApiKeyProjectionCommand.class);
            assertThat(provisioning.state(NOW)).isEqualTo(ApiKeyState.PROVISIONING_FAILED);
        }

        @Test
        @DisplayName("nothing to cancel: no pending change, or the change already failed")
        void nothingToCancel() {
            assertThatThrownBy(() -> active().cancelPendingChange(LATER)).isInstanceOf(ApiKeyUpdateFailure.class);
            assertThatThrownBy(() -> withFailedUpdate().cancelPendingChange(LATER)).isInstanceOf(ApiKeyUpdateFailure.class);
            assertThatThrownBy(() -> deleted().cancelPendingChange(LATER)).isInstanceOf(ApiKeyUpdateFailure.class);
        }

        @Test
        @DisplayName("dismissing registers nothing: the compensating commands went out when the change failed")
        void dismissalsRegisterNothing() {
            ApiKey tombstone = deleted();
            ApiKey failedUpdate = withFailedUpdate();
            ApiKey failedCreation = provisioningFailed();
            tombstone.pullProjectionCommands();
            failedUpdate.pullProjectionCommands();
            failedCreation.pullProjectionCommands();

            tombstone.dismiss();
            failedUpdate.dismiss();
            failedCreation.dismiss();

            assertThat(tombstone.pullProjectionCommands()).isEmpty();
            assertThat(failedUpdate.pullProjectionCommands()).isEmpty();
            assertThat(failedCreation.pullProjectionCommands()).isEmpty();
        }
    }

    @Nested
    @DisplayName("desiredState()")
    class DesiredStateQuery {

        @Test
        void confirmedStateWhenNothingIsPending() {
            assertThat(active().desiredState()).isEqualTo(new DesiredState(NAME, NO_PERMISSIONS, ONE_MONTH_FROM_NOW));
        }

        @Test
        void pendingStateWhenAChangeIsPending() {
            ApiKey key = active();
            key.requestUpdate(new ApiKeyChanges("next", Lifetime.NEVER,
                    new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ))), NOW);

            assertThat(key.desiredState()).isEqualTo(new DesiredState("next", Permissions.none().with(Scope.TENANT, AccessLevel.READ), null));
        }
    }

    @Nested
    @DisplayName("state()")
    class StateQuery {

        @Test
        void allStates() {
            assertThat(provisioning().state(NOW)).isEqualTo(ApiKeyState.PROVISIONING);
            assertThat(provisioningFailed().state(NOW)).isEqualTo(ApiKeyState.PROVISIONING_FAILED);
            assertThat(active().state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(withPendingUpdate().state(NOW)).isEqualTo(ApiKeyState.CHANGE_PENDING);
            assertThat(withPendingDelete().state(NOW)).isEqualTo(ApiKeyState.CHANGE_PENDING);
            assertThat(withFailedUpdate().state(NOW)).isEqualTo(ApiKeyState.CHANGE_FAILED);
            assertThat(deleted().state(NOW)).isEqualTo(ApiKeyState.DELETED);
        }
    }
}
