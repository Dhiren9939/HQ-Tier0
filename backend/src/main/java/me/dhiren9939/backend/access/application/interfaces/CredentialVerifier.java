package me.dhiren9939.backend.access.application.interfaces;

/**
 * Output port that checks a presented secret against a stored hash for one key format.
 * It knows nothing about persistence: the use case supplies the stored hash.
 */
public interface CredentialVerifier {
    /** Whether this verifier handles credentials of the given key format (e.g. {@code V1}). */
    boolean supports(String keyFormat);

    /** Constant-time comparison of the presented secret with the stored hash. */
    boolean matches(String presentedSecret, String storedSecretHash);
}
