package me.dhiren9939.backend.common.access.events.inbound;

/**
 * Why the data plane could not apply a pending API key change. The names are the wire values; mapping them to the
 * Access domain's own failure reason is the application's job.
 */
public enum ApiKeyChangeFailureReason {
    /** The data plane explicitly rejected the change. */
    REJECTED,
    /** The data plane neither confirmed nor rejected the change in time. */
    CONFIRM_TIMEOUT,
    /** The change event could not be published from the outbox. */
    OUTBOX_PUBLISH_FAILURE,
    /** The owner cancelled the change while it was still pending. */
    CANCELLED_BY_USER
}
