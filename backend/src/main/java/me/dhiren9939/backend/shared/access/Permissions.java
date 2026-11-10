package me.dhiren9939.backend.shared.access;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * What an API key may do: one {@link AccessLevel} per {@link Scope}. A scope that is not present means
 * {@link AccessLevel#NONE}. Immutable, never null inside, and free of any framework.
 *
 * <p>Equality ignores explicit {@code NONE} entries, so {@code {TENANT: NONE}} equals no permissions.</p>
 */
public final class Permissions {
    private static final Permissions NONE = new Permissions(new EnumMap<>(Scope.class));

    /** Unmodifiable; holds only levels other than {@code NONE}. */
    private final Map<Scope, AccessLevel> levels;

    private Permissions(EnumMap<Scope, AccessLevel> levels) {
        this.levels = Collections.unmodifiableMap(levels);
    }

    /** No access to any scope. */
    public static Permissions none() {
        return NONE;
    }

    /**
     * Builds permissions from a scope-to-level map. A {@code null} map means none, a {@code null} level
     * means {@code NONE}, and every level must be allowed by its scope.
     */
    public static Permissions of(Map<Scope, AccessLevel> requested) {
        if (requested == null || requested.isEmpty())
            return NONE;
        EnumMap<Scope, AccessLevel> levels = new EnumMap<>(Scope.class);
        requested.forEach((scope, level) -> {
            Objects.requireNonNull(scope, "scope");
            AccessLevel effective = level == null ? AccessLevel.NONE : level;
            scope.requireAllowed(effective);
            if (effective != AccessLevel.NONE)
                levels.put(scope, effective);
        });
        return levels.isEmpty() ? NONE : new Permissions(levels);
    }

    /** The level for {@code scope}; {@code NONE} when it was never granted. */
    public AccessLevel levelOf(Scope scope) {
        return levels.getOrDefault(scope, AccessLevel.NONE);
    }

    /** A copy with {@code scope} set to {@code level}. */
    public Permissions with(Scope scope, AccessLevel level) {
        Objects.requireNonNull(scope, "scope");
        scope.requireAllowed(level);
        EnumMap<Scope, AccessLevel> copy = new EnumMap<>(Scope.class);
        copy.putAll(levels);
        if (level == AccessLevel.NONE)
            copy.remove(scope);
        else
            copy.put(scope, level);
        return copy.isEmpty() ? NONE : new Permissions(copy);
    }

    /** The granted levels, omitting {@code NONE}. Unmodifiable, ordered by scope declaration. */
    public Map<Scope, AccessLevel> asMap() {
        return levels;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Permissions that && levels.equals(that.levels));
    }

    @Override
    public int hashCode() {
        return levels.hashCode();
    }

    @Override
    public String toString() {
        return "Permissions" + levels;
    }
}
