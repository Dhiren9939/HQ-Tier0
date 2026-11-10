package me.dhiren9939.backend.access.domain;

import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.Scope;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Partial permission update: only the scopes listed change; every other scope keeps its level.
 * An empty set of changes changes nothing. Entries with a {@code null} level are dropped (unchanged).
 */
public record PermissionChanges(Map<Scope, AccessLevel> levels) {

    public PermissionChanges {
        EnumMap<Scope, AccessLevel> checked = new EnumMap<>(Scope.class);
        if (levels != null) {
            levels.forEach((scope, level) -> {
                if (scope == null || level == null)
                    return;
                scope.requireAllowed(level);
                checked.put(scope, level);
            });
        }
        levels = Collections.unmodifiableMap(checked);
    }

    public static PermissionChanges none() {
        return new PermissionChanges(Map.of());
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }

    public Permissions applyTo(Permissions current) {
        Permissions result = current;
        for (Map.Entry<Scope, AccessLevel> change : levels.entrySet())
            result = result.with(change.getKey(), change.getValue());
        return result;
    }
}
