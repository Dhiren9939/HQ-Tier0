package me.dhiren9939.backend.access.application.usecase.service.verify;

import me.dhiren9939.backend.common.access.VerificationFailureCode;

/**
 * Internal Access failure for a credential that cannot be accepted. The published
 * {@link me.dhiren9939.backend.common.access.ApiKeyVerificationFacade} catches this and returns a
 * {@link me.dhiren9939.backend.common.access.ApiKeyVerifyResult.Failure} instead, so callers never receive it
 * across a boundary. {@link #code()} says why; the message is safe to show to an end user.
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
}
