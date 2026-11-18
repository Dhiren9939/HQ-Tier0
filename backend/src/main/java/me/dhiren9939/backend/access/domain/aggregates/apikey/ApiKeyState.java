package me.dhiren9939.backend.access.domain.aggregates.apikey;

/** Lifecycle state of an API key at a given moment, derived from its attributes and the clock. */
public enum ApiKeyState {
    /** Issued and in force; authenticates until it expires. */
    ACTIVE,
    /** The expiry has passed. It can be renewed by an update that extends the lifetime. */
    EXPIRED
}
