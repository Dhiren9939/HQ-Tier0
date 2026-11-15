package me.dhiren9939.backend.access.domain.aggregates.apikey;

import me.dhiren9939.backend.access.domain.exceptions.InvalidPermissionsException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A resource area an API key can be granted access to. Each scope lists the levels beyond
 * {@link AccessLevel#NONE} it accepts ({@code NONE} is always allowed).
 *
 * <p>Adding a scope is one new constant here: permissions, updates, persistence, the web layer and
 * the event payload all work from this enum. Consumers must treat a scope they do not know, or a
 * scope missing from a payload, as {@code NONE}.</p>
 */
public enum Scope {
    /** Submitting events for ingestion. */
    INGESTION(AccessLevel.WRITE),
    /** Reading or changing tenant resources. */
    TENANT(AccessLevel.READ, AccessLevel.WRITE, AccessLevel.READ_WRITE),
    /** Reading or changing channel resources. */
    CHANNEL(AccessLevel.READ, AccessLevel.WRITE, AccessLevel.READ_WRITE);

    private final Set<AccessLevel> allowedLevels;

    Scope(AccessLevel... levelsBeyondNone) {
        EnumSet<AccessLevel> levels = EnumSet.of(AccessLevel.NONE);
        levels.addAll(Set.of(levelsBeyondNone));
        this.allowedLevels = Collections.unmodifiableSet(levels);
    }

    /** Every level this scope accepts, always including {@code NONE}. */
    public Set<AccessLevel> allowedLevels() {
        return allowedLevels;
    }

    public boolean allows(AccessLevel level) {
        return allowedLevels.contains(level);
    }

    /** Throws {@link InvalidPermissionsException} unless this scope accepts {@code level}. */
    public void requireAllowed(AccessLevel level) {
        if (level == null || !allows(level))
            throw new InvalidPermissionsException(
                    "Level " + level + " is not allowed for scope " + this + "; allowed: " + allowedLevels);
    }
}
