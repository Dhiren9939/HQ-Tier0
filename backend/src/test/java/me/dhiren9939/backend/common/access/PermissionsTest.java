package me.dhiren9939.backend.common.access;

import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import me.dhiren9939.backend.access.domain.exceptions.InvalidPermissionsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionsTest {

    @Test
    void aScopeThatWasNeverGrantedIsNone() {
        Permissions none = Permissions.none();

        for (Scope scope : Scope.values())
            assertThat(none.levelOf(scope)).isEqualTo(AccessLevel.NONE);
        assertThat(none.asMap()).isEmpty();
    }

    @Test
    void withReturnsACopyAndLeavesTheOriginalUntouched() {
        Permissions original = Permissions.none();

        Permissions changed = original.with(Scope.TENANT, AccessLevel.READ);

        assertThat(original.levelOf(Scope.TENANT)).isEqualTo(AccessLevel.NONE);
        assertThat(changed.levelOf(Scope.TENANT)).isEqualTo(AccessLevel.READ);
        assertThat(changed.levelOf(Scope.CHANNEL)).isEqualTo(AccessLevel.NONE);
    }

    @Test
    void settingALevelBackToNoneRevokesIt() {
        Permissions granted = Permissions.none().with(Scope.TENANT, AccessLevel.READ_WRITE);

        assertThat(granted.with(Scope.TENANT, AccessLevel.NONE)).isEqualTo(Permissions.none());
    }

    @Test
    void explicitNoneEqualsMissing() {
        Permissions explicit = Permissions.of(Map.of(Scope.TENANT, AccessLevel.NONE, Scope.INGESTION, AccessLevel.WRITE));
        Permissions implicit = Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE);

        assertThat(explicit).isEqualTo(implicit).hasSameHashCodeAs(implicit);
        assertThat(explicit.asMap()).doesNotContainKey(Scope.TENANT);
        assertThat(Permissions.of(Map.of(Scope.TENANT, AccessLevel.NONE))).isEqualTo(Permissions.none());
    }

    @Test
    void nullInputsMeanNone() {
        Map<Scope, AccessLevel> withNullLevel = new HashMap<>();
        withNullLevel.put(Scope.TENANT, null);

        assertThat(Permissions.of(null)).isEqualTo(Permissions.none());
        assertThat(Permissions.of(withNullLevel)).isEqualTo(Permissions.none());
    }

    @Test
    void ingestionOnlyAcceptsWrite() {
        assertThat(Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE).levelOf(Scope.INGESTION))
                .isEqualTo(AccessLevel.WRITE);
        assertThatThrownBy(() -> Permissions.none().with(Scope.INGESTION, AccessLevel.READ))
                .isInstanceOf(InvalidPermissionsException.class);
        assertThatThrownBy(() -> Permissions.of(Map.of(Scope.INGESTION, AccessLevel.READ_WRITE)))
                .isInstanceOf(InvalidPermissionsException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Scope.class, names = {"TENANT", "CHANNEL"})
    void tenantAndChannelAcceptEveryLevel(Scope scope) {
        for (AccessLevel level : AccessLevel.values())
            assertThat(Permissions.none().with(scope, level).levelOf(scope)).isEqualTo(level);
    }

    @Test
    void asMapIsUnmodifiable() {
        Permissions permissions = Permissions.none().with(Scope.TENANT, AccessLevel.READ);

        assertThatThrownBy(() -> permissions.asMap().put(Scope.CHANNEL, AccessLevel.READ))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theInputMapIsCopiedSoLaterChangesDoNotLeakIn() {
        Map<Scope, AccessLevel> source = new HashMap<>();
        source.put(Scope.TENANT, AccessLevel.READ);
        Permissions permissions = Permissions.of(source);

        source.put(Scope.CHANNEL, AccessLevel.WRITE);

        assertThat(permissions.levelOf(Scope.CHANNEL)).isEqualTo(AccessLevel.NONE);
    }
}
