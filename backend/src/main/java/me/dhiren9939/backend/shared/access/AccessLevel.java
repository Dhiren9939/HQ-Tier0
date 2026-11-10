package me.dhiren9939.backend.shared.access;

/** How much access an API key has to one {@link Scope}. Which levels a scope accepts is defined by the scope. */
public enum AccessLevel {
    NONE,
    READ,
    WRITE,
    READ_WRITE
}
