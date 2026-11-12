package me.dhiren9939.backend.shared.access;

/**
 * An API key could not be verified. {@link #code()} says why in a form consumers can branch on; the message is
 * written for end users, so a gateway can pass it on as it is.
 */
public class ApiKeyVerificationFailure extends RuntimeException {
    private final VerificationFailureCode code;

    public ApiKeyVerificationFailure(VerificationFailureCode code, String message) {
        super(message);
        this.code = code;
    }

    public VerificationFailureCode code() {
        return code;
    }

    public static ApiKeyVerificationFailure malformed() {
        return new ApiKeyVerificationFailure(VerificationFailureCode.MALFORMED_CREDENTIALS,
                "The API key is missing or not in a recognized format.");
    }

    public static ApiKeyVerificationFailure invalid() {
        return new ApiKeyVerificationFailure(VerificationFailureCode.INVALID_CREDENTIALS,
                "The API key or secret is invalid.");
    }

    public static ApiKeyVerificationFailure expired() {
        return new ApiKeyVerificationFailure(VerificationFailureCode.KEY_EXPIRED,
                "The API key has expired.");
    }

    public static ApiKeyVerificationFailure deleted() {
        return new ApiKeyVerificationFailure(VerificationFailureCode.KEY_DELETED,
                "The API key has been deleted.");
    }

    public static ApiKeyVerificationFailure notActive() {
        return new ApiKeyVerificationFailure(VerificationFailureCode.KEY_NOT_ACTIVE,
                "The API key is not active.");
    }
}
