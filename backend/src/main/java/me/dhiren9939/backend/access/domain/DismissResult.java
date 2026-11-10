package me.dhiren9939.backend.access.domain;

/** What the caller must do after a key was dismissed. */
public enum DismissResult {
    /** The failed change was cleared; the key stays. */
    CLEARED,
    /** The key must be removed. Any event it needs was registered on the aggregate. */
    REMOVED
}
