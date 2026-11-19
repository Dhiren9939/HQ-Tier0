package me.dhiren9939.backend.access.infrastructure.serialization;

/** An integration event could not be written as, or read back from, its JSON form. */
public class IntegrationEventSerializationException extends RuntimeException {

    public IntegrationEventSerializationException(String message) {
        super(message);
    }

    public IntegrationEventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
