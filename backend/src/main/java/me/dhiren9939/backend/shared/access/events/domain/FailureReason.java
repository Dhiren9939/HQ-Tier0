package me.dhiren9939.backend.shared.access.events.domain;

/** Why a pending change failed. */
public enum FailureReason {
    /** The data plane explicitly rejected the change. */
    REJECTED,
    /** The data plane neither confirmed nor rejected the change in time. */
    CONFIRM_TIMEOUT,
    /** The change event could not be published from the outbox. */
    OUTBOX_PUBLISH_FAILURE,
    /** The owner cancelled the change while it was still pending. */
    CANCELLED_BY_USER
}
