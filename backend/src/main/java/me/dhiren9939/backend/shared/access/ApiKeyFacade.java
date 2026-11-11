package me.dhiren9939.backend.shared.access;

/**
 * Public entry point for verifying API key credentials.
 *
 * <p>When the credentials cannot be accepted, {@link #verifyApiKey} throws an {@link ApiKeyVerificationFailure}.
 * Branch on its {@link ApiKeyVerificationFailure#code() code}; its message is written for end users and can be
 * passed on as it is. Codes: {@link VerificationFailureCode#MALFORMED_CREDENTIALS},
 * {@link VerificationFailureCode#INVALID_CREDENTIALS} (unknown id and wrong secret are one outcome),
 * {@link VerificationFailureCode#KEY_EXPIRED}, {@link VerificationFailureCode#KEY_DELETED} and
 * {@link VerificationFailureCode#KEY_NOT_ACTIVE}; the last three are only returned once the secret matched.</p>
 */
public interface ApiKeyFacade {
    ApiKeyVerifyDto verifyApiKey(String accessId, String accessSecret);
}
