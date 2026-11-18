package me.dhiren9939.backend.common.access;

/**
 * Machine-readable reason an API key could not be verified. Consumers branch on this; the failure's message is
 * safe to show to the end user. Only {@link #MALFORMED_CREDENTIALS} and {@link #INVALID_CREDENTIALS} can be
 * returned to a caller who does not hold the right secret: the other codes are reported only after the secret
 * matched, so they never reveal whether an access id exists.
 */
public enum VerificationFailureCode {
    /** The access id or secret is missing or not in a recognized format. */
    MALFORMED_CREDENTIALS,
    /** The access id is unknown, or the secret does not match. Deliberately one outcome for both. */
    INVALID_CREDENTIALS,
    /** The credentials are right but the key's expiry has passed. */
    KEY_EXPIRED
}
