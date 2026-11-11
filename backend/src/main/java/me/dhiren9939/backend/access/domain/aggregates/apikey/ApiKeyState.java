package me.dhiren9939.backend.access.domain.aggregates.apikey;

/** Lifecycle state of an API key at a given moment, derived from its confirmed state, its pending change and the clock. */
public enum ApiKeyState {
    /** Creation requested, not yet confirmed. Readable by the owner; cannot authenticate. */
    PROVISIONING,
    /** Creation failed; awaiting dismiss. */
    PROVISIONING_FAILED,
    /** Confirmed, no pending change. */
    ACTIVE,
    /** An update or delete awaits confirmation. The key still authenticates with its confirmed attributes. */
    CHANGE_PENDING,
    /** An update or delete failed; awaiting dismiss. The key still authenticates with its confirmed attributes. */
    CHANGE_FAILED,
    /**
     * The confirmed expiry has passed. Takes precedence over a pending or failed change, because it is the
     * fact that matters to whoever uses the key; the change is still there and applying it can renew the key.
     */
    EXPIRED,
    /** Delete confirmed. Tombstone awaiting dismiss. */
    DELETED
}
