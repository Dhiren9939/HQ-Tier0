package me.dhiren9939.backend.common.access.events.outbound;

import java.util.Arrays;
import java.util.Optional;

/**
 * The levels a granted scope can have, as published. "No access" is never published: the scope is left out.
 *
 * <p>{@link #wireName()} is what consumers see and is part of the contract: it never changes, even if a constant is
 * renamed.</p>
 */
public enum ApiKeyAccessLevel {
    READ("READ"),
    WRITE("WRITE"),
    READ_WRITE("READ_WRITE");

    private final String wireName;

    ApiKeyAccessLevel(String wireName) {
        this.wireName = wireName;
    }

    /** How this level is written in published events. */
    public String wireName() {
        return wireName;
    }

    /** The level published as {@code wireName}, if there is one. */
    public static Optional<ApiKeyAccessLevel> fromWire(String wireName) {
        return Arrays.stream(values()).filter(level -> level.wireName.equals(wireName)).findFirst();
    }
}
