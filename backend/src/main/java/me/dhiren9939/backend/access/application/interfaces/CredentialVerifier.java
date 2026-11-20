package me.dhiren9939.backend.access.application.interfaces;

/**
 * Checks a presented credential for one key format.
 * It knows nothing about persistence: the use case supplies the stored hash.
 */
public interface CredentialVerifier {
    /** Whether this verifier handles credentials of the given key format (e.g. {@code V1}). */
    boolean supports(String keyFormat);

    /**
     * Whether the credential has exactly the shape this format issues (lengths, alphabet). The use case asks before
     * the lookup and the hash, so this must be cheap for input of any size: compare lengths first.
     */
    boolean isWellFormed(String accessId, String accessSecret);

    /** Constant-time comparison of the presented secret with the stored hash. */
    boolean matches(String presentedSecret, String storedSecretHash);
}
