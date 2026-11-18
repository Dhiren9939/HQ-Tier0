package me.dhiren9939.backend.access.infrastructure.messaging;

/** An outbox row that can never become a message, for example because its permissions column is not a JSON object. */
public class UnpublishableEventException extends RuntimeException {

    public UnpublishableEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnpublishableEventException(String message) {
        super(message);
    }
}
