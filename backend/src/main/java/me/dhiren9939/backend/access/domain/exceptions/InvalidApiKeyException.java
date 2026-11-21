package me.dhiren9939.backend.access.domain.exceptions;

/**
 * The caller asked for a key that breaks one of the aggregate's rules. {@link #violation()} says which one. The
 * aggregate checks its own rules whoever calls it, so this is what a caller that skipped the request DTO gets.
 *
 * <p>Rules that only server code can break (a credential from the issuer, a timestamp from the clock, the event
 * contract) are not reported this way: those are bugs and fail as {@link IllegalArgumentException} or
 * {@link NullPointerException}.</p>
 */
public class InvalidApiKeyException extends RuntimeException {
    private final ApiKeyViolation violation;

    public InvalidApiKeyException(ApiKeyViolation violation) {
        this(violation, violation.message());
    }

    public InvalidApiKeyException(ApiKeyViolation violation, String message) {
        super(message);
        this.violation = violation;
    }

    public ApiKeyViolation violation() {
        return violation;
    }
}
