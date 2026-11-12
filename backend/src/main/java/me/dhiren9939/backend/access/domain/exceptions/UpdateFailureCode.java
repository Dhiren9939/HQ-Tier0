package me.dhiren9939.backend.access.domain.exceptions;

/** Why an operation on an API key was refused. The name is the machine-readable code clients see. */
public enum UpdateFailureCode {
    /** The key's creation is still waiting for the data plane. */
    API_KEY_CREATION_PENDING,
    /** The key's creation failed; the owner has to dismiss it first. */
    API_KEY_CREATION_FAILED,
    /** An update or delete is still waiting for the data plane. */
    API_KEY_CHANGE_PENDING,
    /** An update or delete failed; the owner has to dismiss it first. */
    API_KEY_CHANGE_FAILED,
    /** The key has been deleted. */
    API_KEY_DELETED,
    /** There is no pending change to confirm, reject or cancel. */
    NO_PENDING_CHANGE,
    /** The revision being answered is not the pending one (stale or from the future). */
    REVISION_MISMATCH,
    /** The key is not in a state that can be dismissed. */
    NOTHING_TO_DISMISS,
    /** Concurrent changes kept winning the race and the retries ran out. */
    CONCURRENT_MODIFICATION
}
