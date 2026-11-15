package me.dhiren9939.backend.access.domain.aggregates.apikey;

/** How much access an API key has to one {@link Scope}. Which levels a scope accepts is defined by the scope. */
public enum AccessLevel {
    NONE,
    READ,
    WRITE,
    READ_WRITE
}
