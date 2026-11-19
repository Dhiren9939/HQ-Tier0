package me.dhiren9939.backend.access.infrastructure.messaging;

/**
 * The broker or the connection to it could not take the message right now (closed producer, reconnecting, stream not
 * available, internal broker error, ...). Nothing is wrong with the message: the relay cancels the batch and the next
 * tick sends it again.
 */
public class ProjectionUnavailableException extends RuntimeException {
    public ProjectionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
