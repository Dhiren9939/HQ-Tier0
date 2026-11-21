package me.dhiren9939.backend.access.domain.aggregates.apikey;

import me.dhiren9939.backend.access.domain.events.ApiKeyCreated;
import me.dhiren9939.backend.access.domain.events.ApiKeyDeleted;
import me.dhiren9939.backend.access.domain.events.ApiKeyEvent;
import me.dhiren9939.backend.access.domain.events.ApiKeyUpdated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static ApiKey issued() {
        return ApiKey.issue(USER_ID, NAME, CREDENTIAL, NO_PERMISSIONS, Lifetime.ONE_MONTH, NOW);
    }

    private static ApiKey issuedWithoutExpiry() {
        return ApiKey.issue(USER_ID, NAME, CREDENTIAL, NO_PERMISSIONS, Lifetime.NEVER, NOW);
    }

    private static ApiKey activeWithoutEvents() {
        ApiKey key = issued();
        key.pullDomainEvents();
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
        @DisplayName("creates an active key at version 1")
        void happyPath() {
            ApiKey key = issued();

            assertThat(key.getApiKeyId()).isNotNull();
            assertThat(key.getUserId()).isEqualTo(USER_ID);
            assertThat(key.getApiKeyName()).isEqualTo(NAME);
            assertThat(key.getAccessId()).isEqualTo("V1_acc");
            assertThat(key.getAccessSecretHash()).isEqualTo("hashed_secret");
            assertThat(key.getPermissions()).isEqualTo(NO_PERMISSIONS);
            assertThat(key.getCreatedAt()).isEqualTo(NOW);
            assertThat(key.getExpiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
            assertThat(key.getRevision()).isEqualTo(1);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(key.canAuthenticate(NOW)).isTrue();
        }

        @Test
        @DisplayName("preserves a null expiry for non-expiring keys")
        void nullExpiry() {
            ApiKey key = issuedWithoutExpiry();

            assertThat(key.getExpiresAt()).isNull();
            assertThat(key.state(Instant.parse("2099-01-01T00:00:00Z"))).isEqualTo(ApiKeyState.ACTIVE);
        }

        @Test
        @DisplayName("a fresh key is readable by its owner")
        void readableByOwner() {
            assertThat(issued().isOwnedBy(USER_ID)).isTrue();
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        void ownerOnly() {
            ApiKey key = issued();

            assertThat(key.isOwnedBy(USER_ID)).isTrue();
            assertThat(key.isOwnedBy(OTHER_USER_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("canAuthenticate()")
    class CanAuthenticate {

        @Test
        void activeKey() {
            assertThat(issued().canAuthenticate(NOW)).isTrue();
        }

        @Test
        void nonExpiringKey() {
            assertThat(issuedWithoutExpiry().canAuthenticate(Instant.parse("2099-01-01T00:00:00Z"))).isTrue();
        }

        @Test
        void expiredKey() {
            assertThat(issued().canAuthenticate(ONE_MONTH_FROM_NOW.plusSeconds(1))).isFalse();
        }
    }

    @Nested
    @DisplayName("state(now) and expiry")
    class ExpiredState {
        private final Instant afterExpiry = ONE_MONTH_FROM_NOW.plusSeconds(1);

        @Test
        void aKeyPastItsExpiryIsExpired() {
            assertThat(issued().state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(issued().state(ONE_MONTH_FROM_NOW)).as("the expiry instant itself is still valid").isEqualTo(ApiKeyState.ACTIVE);
            assertThat(issued().state(afterExpiry)).isEqualTo(ApiKeyState.EXPIRED);
        }

        @Test
        void aKeyThatNeverExpiresIsNeverExpired() {
            assertThat(issuedWithoutExpiry().state(Instant.parse("2099-01-01T00:00:00Z"))).isEqualTo(ApiKeyState.ACTIVE);
        }

        @Test
        @DisplayName("an expired key cannot authenticate but can still be updated or deleted")
        void anExpiredKeyCanStillBeRenewedOrDeleted() {
            ApiKey key = activeWithoutEvents();

            assertThat(key.canAuthenticate(afterExpiry)).isFalse();
            assertThat(key.update(new ApiKeyChanges(null, Lifetime.ONE_MONTH, null), afterExpiry)).isTrue();
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("updates the name at once")
        void nameOnly() {
            ApiKey key = activeWithoutEvents();

            boolean changed = key.update(name("new-name"), LATER);

            assertThat(changed).isTrue();
            assertThat(key.getRevision()).isEqualTo(2);
            assertThat(key.getApiKeyName()).isEqualTo("new-name");
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
        }

        @Test
        @DisplayName("updates the lifetime, restarting it from now")
        void lifetime() {
            ApiKey key = activeWithoutEvents();

            boolean changed = key.update(new ApiKeyChanges(null, Lifetime.ONE_DAY, null), LATER);

            assertThat(changed).isTrue();
            assertThat(key.getExpiresAt()).isEqualTo(ONE_DAY_FROM_LATER);
        }

        @Test
        @DisplayName("lifetime NEVER clears the expiry")
        void neverExpires() {
            ApiKey key = activeWithoutEvents();

            boolean changed = key.update(new ApiKeyChanges(null, Lifetime.NEVER, null), NOW);

            assertThat(changed).isTrue();
            assertThat(key.getExpiresAt()).isNull();
        }

        @Test
        @DisplayName("updates ingestion permission only")
        void ingestionPermission() {
            ApiKey key = activeWithoutEvents();

            key.update(permissions(new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE))), NOW);

            assertThat(key.getPermissions()).isEqualTo(Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE));
        }

        @Test
        @DisplayName("updates tenant permission only")
        void tenantPermission() {
            ApiKey key = activeWithoutEvents();

            key.update(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ_WRITE))), NOW);

            assertThat(key.getPermissions()).isEqualTo(Permissions.none().with(Scope.TENANT, AccessLevel.READ_WRITE));
        }

        @Test
        @DisplayName("updates channel permission only")
        void channelPermission() {
            ApiKey key = activeWithoutEvents();

            key.update(permissions(new PermissionChanges(Map.of(Scope.CHANNEL, AccessLevel.READ_WRITE))), NOW);

            assertThat(key.getPermissions()).isEqualTo(Permissions.none().with(Scope.CHANNEL, AccessLevel.READ_WRITE));
        }

        @Test
        @DisplayName("updates all fields at once")
        void allFields() {
            ApiKey key = activeWithoutEvents();
            Permissions expected = Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE).with(Scope.TENANT, AccessLevel.READ).with(Scope.CHANNEL, AccessLevel.WRITE);

            key.update(new ApiKeyChanges("all-new", Lifetime.ONE_DAY, new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE, Scope.TENANT, AccessLevel.READ, Scope.CHANNEL, AccessLevel.WRITE))), LATER);

            assertThat(key.getRevision()).isEqualTo(2);
            assertThat(key.getApiKeyName()).isEqualTo("all-new");
            assertThat(key.getPermissions()).isEqualTo(expected);
            assertThat(key.getExpiresAt()).isEqualTo(ONE_DAY_FROM_LATER);
        }

        @Test
        @DisplayName("no-op when nothing is requested")
        void noOpAllNull() {
            ApiKey key = activeWithoutEvents();

            assertThat(key.update(new ApiKeyChanges(null, null, null), NOW)).isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
        }

        @Test
        @DisplayName("no-op when the name is the same")
        void noOpSameName() {
            ApiKey key = activeWithoutEvents();

            assertThat(key.update(name(NAME), NOW)).isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
        }

        @Test
        @DisplayName("no-op when every permission level is the same")
        void noOpSamePermissions() {
            ApiKey key = activeWithoutEvents();

            assertThat(key.update(permissions(new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.NONE, Scope.TENANT, AccessLevel.NONE, Scope.CHANNEL, AccessLevel.NONE))), NOW))
                    .isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
        }

        @Test
        @DisplayName("no-op when the permission changes are empty")
        void noOpEmptyPermissionChanges() {
            ApiKey key = activeWithoutEvents();

            assertThat(key.update(permissions(PermissionChanges.none()), NOW)).isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
            assertThat(key.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("no-op when a granted level is requested again")
        void noOpSameGrantedLevel() {
            ApiKey key = activeWithoutEvents();
            key.update(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ))), NOW);

            assertThat(key.update(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ))), LATER))
                    .isFalse();

            assertThat(key.getRevision()).isEqualTo(2);
        }

        @Test
        @DisplayName("setting a scope to NONE revokes it at once")
        void revokingAScope() {
            ApiKey key = activeWithoutEvents();
            key.update(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ_WRITE))), NOW);
            assertThat(key.getPermissions().levelOf(Scope.TENANT)).isEqualTo(AccessLevel.READ_WRITE);

            assertThat(key.update(permissions(new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.NONE))), LATER))
                    .isTrue();

            assertThat(key.getPermissions()).isEqualTo(Permissions.none());
        }

        @Test
        @DisplayName("an unrelated scope keeps its level when another one changes")
        void otherScopesAreUntouched() {
            ApiKey key = activeWithoutEvents();
            key.update(permissions(new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE))), NOW);

            key.update(permissions(new PermissionChanges(Map.of(Scope.CHANNEL, AccessLevel.READ))), LATER);

            assertThat(key.getPermissions()).isEqualTo(Permissions.none()
                    .with(Scope.INGESTION, AccessLevel.WRITE).with(Scope.CHANNEL, AccessLevel.READ));
        }

        @Test
        @DisplayName("no-op when the computed expiry equals the current expiry")
        void noOpSameExpiry() {
            ApiKey key = activeWithoutEvents();

            assertThat(key.update(new ApiKeyChanges(null, Lifetime.ONE_MONTH, null), NOW)).isFalse();

            assertThat(key.getRevision()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("bumps the revision and registers the deletion event; the attributes stay as they were")
        void happyPath() {
            ApiKey key = activeWithoutEvents();

            key.delete(LATER);

            assertThat(key.getRevision()).isEqualTo(2);
            assertThat(key.state(NOW)).isEqualTo(ApiKeyState.ACTIVE);
            assertThat(key.isOwnedBy(USER_ID)).isTrue();
            assertThat(key.getApiKeyName()).isEqualTo(NAME);
        }
    }

    @Nested
    @DisplayName("domain events")
    class DomainEvents {

        @Test
        @DisplayName("issuing registers ApiKeyCreated at version 1 with the secret hash and occurredAt = now")
        void issueRegistersCreated() {
            ApiKey key = issued();

            List<ApiKeyEvent> events = key.pullDomainEvents();

            assertThat(events).hasSize(1);
            ApiKeyCreated created = (ApiKeyCreated) events.getFirst();
            assertThat(created.apiKeyId()).isEqualTo(key.getApiKeyId());
            assertThat(created.version()).isEqualTo(1);
            assertThat(created.occurredAt()).isEqualTo(NOW);
            assertThat(created.userId()).isEqualTo(USER_ID);
            assertThat(created.accessId()).isEqualTo("V1_acc");
            assertThat(created.accessSecretHash()).isEqualTo("hashed_secret");
            assertThat(created.apiKeyName()).isEqualTo(NAME);
            assertThat(created.permissions()).isEqualTo(NO_PERMISSIONS);
            assertThat(created.createdAt()).isEqualTo(NOW);
            assertThat(created.expiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
        }

        @Test
        @DisplayName("pulling hands the events over once")
        void pullClears() {
            ApiKey key = issued();

            assertThat(key.pullDomainEvents()).hasSize(1);
            assertThat(key.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("an update registers ApiKeyUpdated with the new state at the next version")
        void updateRegistersUpdated() {
            ApiKey key = activeWithoutEvents();

            key.update(new ApiKeyChanges("renamed", Lifetime.NEVER,
                    new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE))), LATER);

            ApiKeyUpdated updated = (ApiKeyUpdated) key.pullDomainEvents().getFirst();
            assertThat(updated.version()).isEqualTo(2);
            assertThat(updated.occurredAt()).isEqualTo(LATER);
            assertThat(updated.apiKeyName()).isEqualTo("renamed");
            assertThat(updated.permissions().levelOf(Scope.INGESTION)).isEqualTo(AccessLevel.WRITE);
            assertThat(updated.createdAt()).isEqualTo(NOW);
            assertThat(updated.expiresAt()).isNull();
            assertThat(updated.accessSecretHash()).as("full snapshot").isEqualTo(key.getAccessSecretHash());
        }

        @Test
        @DisplayName("an update that changes nothing registers no event")
        void noOpRegistersNothing() {
            ApiKey key = activeWithoutEvents();

            assertThat(key.update(name(NAME), LATER)).isFalse();

            assertThat(key.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("deleting registers ApiKeyDeleted at the next version")
        void deletionRegistersDeleted() {
            ApiKey key = activeWithoutEvents();

            key.delete(LATER);

            ApiKeyDeleted deleted = (ApiKeyDeleted) key.pullDomainEvents().getFirst();
            assertThat(deleted.version()).isEqualTo(2);
            assertThat(deleted.occurredAt()).isEqualTo(LATER);
            assertThat(deleted.apiKeyName()).isEqualTo(NAME);
            assertThat(deleted.accessId()).isEqualTo("V1_acc");
            assertThat(deleted.expiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
            assertThat(deleted.accessSecretHash()).as("full snapshot").isEqualTo(key.getAccessSecretHash());
        }
    }
}
