package me.dhiren9939.backend.access.infrastructure.messaging;

/** The broker answered a publish with an error about the message itself; publishing it again will not help. */
public class ProjectionRefusedException extends RuntimeException {
    public ProjectionRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
