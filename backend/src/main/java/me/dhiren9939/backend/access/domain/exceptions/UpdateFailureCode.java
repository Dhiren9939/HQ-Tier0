package me.dhiren9939.backend.access.domain.exceptions;

/** Why an operation on an API key was refused. The name is the machine-readable code clients see. */
public enum UpdateFailureCode {
    /** The key has been deleted. */
    API_KEY_DELETED,
    /** The key is not in a state that can be dismissed. */
    NOTHING_TO_DISMISS,
    /** Concurrent changes kept winning the race and the retries ran out. */
    CONCURRENT_MODIFICATION
}
