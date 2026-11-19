package me.dhiren9939.backend.common.access.events.outbound;

/** The levels a granted scope can have, as published. "No access" is never published: the scope is left out. */
public enum ApiKeyAccessLevel {
    READ,
    WRITE,
    READ_WRITE
}
