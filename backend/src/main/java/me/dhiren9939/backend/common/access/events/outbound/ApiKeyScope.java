package me.dhiren9939.backend.common.access.events.outbound;

import java.util.Arrays;
import java.util.Optional;

/**
 * The scopes an API key can be granted, as published. A scope missing from {@code permissions} means no access.
 *
 * <p>{@link #wireName()} is what consumers see and is part of the contract: it never changes, even if a constant is
 * renamed. Adding a scope means a new constant with a new wire name, which consumers must treat as unknown (no
 * access) until they learn it.</p>
 */
public enum ApiKeyScope {
    INGESTION("INGESTION"),
    TENANT("TENANT"),
    CHANNEL("CHANNEL");

    private final String wireName;

    ApiKeyScope(String wireName) {
        this.wireName = wireName;
    }

    /** How this scope is written in published events. */
    public String wireName() {
        return wireName;
    }

    /** The scope published as {@code wireName}, if there is one. */
    public static Optional<ApiKeyScope> fromWire(String wireName) {
        return Arrays.stream(values()).filter(scope -> scope.wireName.equals(wireName)).findFirst();
    }
}
