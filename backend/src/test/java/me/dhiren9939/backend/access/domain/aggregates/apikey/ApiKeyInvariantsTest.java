package me.dhiren9939.backend.access.domain.aggregates.apikey;

import me.dhiren9939.backend.access.domain.exceptions.ApiKeyViolation;
import me.dhiren9939.backend.access.domain.exceptions.InvalidApiKeyException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The aggregate checks its own rules whoever calls it, so a caller that skips the request DTO gets a domain error
 * with a code, never a 500.
 */
class ApiKeyInvariantsTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final IssuedCredential CREDENTIAL = new IssuedCredential("V1_acc", "secret_raw", "hashed_secret");

    private static ApiKey issue(UUID userId, String name, Permissions permissions, Lifetime lifetime) {
        return ApiKey.issue(userId, name, CREDENTIAL, permissions, lifetime, NOW);
    }

    private static void assertViolation(ThrowingCallable action, ApiKeyViolation violation) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(InvalidApiKeyException.class,
                        e -> assertThat(e.violation()).isEqualTo(violation));
    }

    // ---- issue

    @Test
    void aValidKeyIsIssued() {
        ApiKey key = issue(USER_ID, "n".repeat(ApiKey.MAX_NAME_LENGTH), Permissions.none(), Lifetime.ONE_DAY);

        assertThat(key.getApiKeyName()).hasSize(256);
        assertThat(key.getExpiresAt()).isEqualTo(NOW.plusSeconds(86_400));
    }

    @Test
    void theOwnerIsRequired() {
        assertViolation(() -> issue(null, "k", Permissions.none(), Lifetime.NEVER), ApiKeyViolation.USER_REQUIRED);
    }

    @Test
    void theNameIsRequired() {
        assertViolation(() -> issue(USER_ID, null, Permissions.none(), Lifetime.NEVER), ApiKeyViolation.NAME_REQUIRED);
        assertViolation(() -> issue(USER_ID, "  ", Permissions.none(), Lifetime.NEVER), ApiKeyViolation.NAME_REQUIRED);
    }

    @Test
    void theNameHasAMaximumLength() {
        assertViolation(() -> issue(USER_ID, "n".repeat(257), Permissions.none(), Lifetime.NEVER),
                ApiKeyViolation.NAME_TOO_LONG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"line\nbreak", "tab\there", "nul\u0000", "escape\u001b[31m", "c1\u0085control"})
    void theNameHasNoControlCharacters(String name) {
        assertViolation(() -> issue(USER_ID, name, Permissions.none(), Lifetime.NEVER),
                ApiKeyViolation.NAME_INVALID_CHARACTERS);
    }

    @Test
    void theLifetimeIsRequired() {
        assertViolation(() -> issue(USER_ID, "k", Permissions.none(), null), ApiKeyViolation.EXPIRY_REQUIRED);
    }

    @Test
    void thePermissionsAreRequired() {
        assertViolation(() -> issue(USER_ID, "k", null, Lifetime.NEVER), ApiKeyViolation.PERMISSIONS_REQUIRED);
    }

    @Test
    void aPermissionNeedsAScopeAndALevelItAllows() {
        Map<Scope, AccessLevel> noScope = new HashMap<>();
        noScope.put(null, AccessLevel.READ);

        assertViolation(() -> Permissions.of(noScope), ApiKeyViolation.PERMISSION_SCOPE_REQUIRED);
        assertViolation(() -> Permissions.none().with(null, AccessLevel.READ), ApiKeyViolation.PERMISSION_SCOPE_REQUIRED);
        assertViolation(() -> Permissions.of(Map.of(Scope.INGESTION, AccessLevel.READ)), ApiKeyViolation.INVALID_SCOPE_LEVEL);
    }

    @Test
    void serverSuppliedValuesAreBugsNotCallerErrors() {
        assertThatThrownBy(() -> ApiKey.issue(USER_ID, "k", null, Permissions.none(), Lifetime.NEVER, NOW))
                .isNotInstanceOf(InvalidApiKeyException.class);
        assertThatThrownBy(() -> ApiKey.issue(USER_ID, "k", CREDENTIAL, Permissions.none(), Lifetime.NEVER, null))
                .isNotInstanceOf(InvalidApiKeyException.class);
    }

    // ---- update

    @Test
    void anUpdatedNameFollowsTheSameRulesAndARefusedUpdateChangesNothing() {
        ApiKey key = issue(USER_ID, "k", Permissions.none(), Lifetime.NEVER);
        key.pullDomainEvents();

        assertViolation(() -> key.update(new ApiKeyChanges(" ", null, null), NOW), ApiKeyViolation.NAME_BLANK);
        assertViolation(() -> key.update(new ApiKeyChanges("n".repeat(257), null, null), NOW),
                ApiKeyViolation.NAME_TOO_LONG);
        assertViolation(() -> key.update(new ApiKeyChanges("bad\nname", Lifetime.ONE_DAY, null), NOW),
                ApiKeyViolation.NAME_INVALID_CHARACTERS);

        assertThat(key.getApiKeyName()).isEqualTo("k");
        assertThat(key.getExpiresAt()).isNull();
        assertThat(key.getRevision()).isEqualTo(1);
        assertThat(key.pullDomainEvents()).isEmpty();
    }

    @Test
    void aMissingNameInAnUpdateMeansUnchanged() {
        ApiKey key = issue(USER_ID, "k", Permissions.none(), Lifetime.NEVER);

        assertThat(key.update(new ApiKeyChanges(null, Lifetime.ONE_DAY, null), NOW)).isTrue();
        assertThat(key.getApiKeyName()).isEqualTo("k");
    }
}
