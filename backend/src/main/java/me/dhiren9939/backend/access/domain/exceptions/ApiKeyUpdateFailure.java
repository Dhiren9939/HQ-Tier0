package me.dhiren9939.backend.access.domain.exceptions;

/** An operation on an API key was refused because of the key's state. {@link #code()} says exactly why. */
public class ApiKeyUpdateFailure extends RuntimeException {
    private final UpdateFailureCode code;

    public ApiKeyUpdateFailure(UpdateFailureCode code, String message) {
        super(message);
        this.code = code;
    }

    public UpdateFailureCode code() {
        return code;
    }
}
