package me.dhiren9939.backend.access.domain.exceptions;

/** Why an operation on an API key was refused. The name is the machine-readable code clients see. */
public enum UpdateFailureCode {
    /** Concurrent changes kept winning the race and the retries ran out. */
    CONCURRENT_MODIFICATION
}
