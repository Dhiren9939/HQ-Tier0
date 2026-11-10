package me.dhiren9939.backend.access.domain;

/** Why a pending change failed. */
public enum FailureReason {
    /** The data plane explicitly rejected the change. */
    REJECTED,
    /** The data plane neither confirmed nor rejected the change in time. */
    CONFIRM_TIMEOUT,
    /** The change event could not be published from the outbox. */
    OUTBOX_PUBLISH_FAILURE
}
