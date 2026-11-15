package me.dhiren9939.backend.common.access;

/**
 * Outcome of verifying an API key. A successful result carries the key view; a failed result carries the
 * machine-readable reason and an end-user-safe message. The facade returns this for both outcomes and never
 * throws a verification exception, so it is safe to pass across a process or network boundary.
 */
public sealed interface ApiKeyVerifyResult
        permits ApiKeyVerifyResult.Success, ApiKeyVerifyResult.Failure {

    record Success(ApiKeyView apiKeyView) implements ApiKeyVerifyResult {
    }

    record Failure(VerificationFailureCode reason, String message) implements ApiKeyVerifyResult {
    }
}
