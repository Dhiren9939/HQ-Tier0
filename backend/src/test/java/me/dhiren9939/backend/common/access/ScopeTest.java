package me.dhiren9939.backend.common.access;

import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adding a scope is one enum constant. These checks run over {@code Scope.values()}, so a new scope is
 * covered automatically and fails here if it is declared inconsistently.
 */
class ScopeTest {

    @ParameterizedTest
    @EnumSource(Scope.class)
    void everyScopeAllowsNoneAndAtLeastOneRealLevel(Scope scope) {
        assertThat(scope.allowedLevels()).contains(AccessLevel.NONE);
        assertThat(scope.allowedLevels()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @EnumSource(Scope.class)
    void aGrantInEveryAllowedLevelRoundTripsThroughPermissions(Scope scope) {
        for (AccessLevel level : scope.allowedLevels())
            assertThat(Permissions.none().with(scope, level).levelOf(scope)).isEqualTo(level);
    }
}
