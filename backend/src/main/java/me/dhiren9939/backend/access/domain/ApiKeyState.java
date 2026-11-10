package me.dhiren9939.backend.access.domain;

/** Lifecycle state of an API key, derived from its confirmed state and pending change. */
public enum ApiKeyState {
    /** Creation requested, not yet confirmed. Readable by the owner; cannot authenticate. */
    PROVISIONING,
    /** Creation failed; awaiting dismiss. */
    PROVISIONING_FAILED,
    /** Confirmed, no pending change. */
    ACTIVE,
    /** An update or delete awaits confirmation. */
    CHANGE_PENDING,
    /** An update or delete failed; awaiting dismiss. */
    CHANGE_FAILED,
    /** Delete confirmed. Tombstone awaiting dismiss. */
    DELETED
}
