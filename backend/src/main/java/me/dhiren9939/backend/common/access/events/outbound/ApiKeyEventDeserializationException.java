package me.dhiren9939.backend.common.access.events.outbound;

/** A published apiKey.projection body does not meet the contract; the message names the field. */
public class ApiKeyEventDeserializationException extends RuntimeException {

    public ApiKeyEventDeserializationException(String message) {
        super(message);
    }

    public ApiKeyEventDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
