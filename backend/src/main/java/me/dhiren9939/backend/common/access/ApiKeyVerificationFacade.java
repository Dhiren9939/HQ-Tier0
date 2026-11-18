package me.dhiren9939.backend.common.access;

/**
 * Public entry point for verifying API key credentials.
 *
 * <p>{@link #verifyApiKey} always returns a {@link ApiKeyVerifyResult}: success carries an {@link ApiKeyView},
 * and failure carries a {@link ApiKeyVerifyResult.Failure#reason() reason} plus an end-user-safe message. It never
 * throws for rejected credentials, so the result can cross a process or network boundary. Consumers decide how to
 * turn a reason into their own exception. Codes: {@link VerificationFailureCode#MALFORMED_CREDENTIALS},
 * {@link VerificationFailureCode#INVALID_CREDENTIALS} (unknown id and wrong secret are one outcome) and
 * {@link VerificationFailureCode#KEY_EXPIRED}; the last one is only returned once the secret matched.</p>
 */
public interface ApiKeyVerificationFacade {
    ApiKeyVerifyResult verifyApiKey(VerifyApiKeyQuery query);
}
