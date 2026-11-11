package me.dhiren9939.backend.access.domain.aggregates.apikey;

import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.InvalidPermissionsException;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.Scope;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionChangesTest {

    private static final Permissions CURRENT = Permissions.none()
            .with(Scope.INGESTION, AccessLevel.WRITE)
            .with(Scope.TENANT, AccessLevel.READ);

    @Test
    void onlyTheListedScopesChange() {
        Permissions result = new PermissionChanges(Map.of(Scope.CHANNEL, AccessLevel.READ_WRITE)).applyTo(CURRENT);

        assertThat(result.levelOf(Scope.INGESTION)).isEqualTo(AccessLevel.WRITE);
        assertThat(result.levelOf(Scope.TENANT)).isEqualTo(AccessLevel.READ);
        assertThat(result.levelOf(Scope.CHANNEL)).isEqualTo(AccessLevel.READ_WRITE);
    }

    @Test
    void noneRevokesAScope() {
        Permissions result = new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.NONE)).applyTo(CURRENT);

        assertThat(result).isEqualTo(Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE));
    }

    @Test
    void emptyChangesLeaveEverythingAsItIs() {
        assertThat(PermissionChanges.none().isEmpty()).isTrue();
        assertThat(PermissionChanges.none().applyTo(CURRENT)).isEqualTo(CURRENT);
        assertThat(new PermissionChanges(null).applyTo(CURRENT)).isEqualTo(CURRENT);
    }

    @Test
    void nullLevelsAreDroppedAndMeanUnchanged() {
        Map<Scope, AccessLevel> requested = new HashMap<>();
        requested.put(Scope.TENANT, null);

        PermissionChanges changes = new PermissionChanges(requested);

        assertThat(changes.isEmpty()).isTrue();
        assertThat(changes.applyTo(CURRENT)).isEqualTo(CURRENT);
    }

    @Test
    void aLevelTheScopeDoesNotAcceptIsRejectedWhenTheChangesAreBuilt() {
        assertThatThrownBy(() -> new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.READ)))
                .isInstanceOf(InvalidPermissionsException.class);
    }

    @Test
    void theChangesAreCopiedAndUnmodifiable() {
        Map<Scope, AccessLevel> source = new HashMap<>(Map.of(Scope.TENANT, AccessLevel.READ));
        PermissionChanges changes = new PermissionChanges(source);

        source.put(Scope.CHANNEL, AccessLevel.READ);

        assertThat(changes.levels()).containsOnlyKeys(Scope.TENANT);
        assertThatThrownBy(() -> changes.levels().put(Scope.CHANNEL, AccessLevel.READ))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
