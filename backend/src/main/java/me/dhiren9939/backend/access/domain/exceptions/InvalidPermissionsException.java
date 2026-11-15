package me.dhiren9939.backend.access.domain.exceptions;

/** Indicates a permission level that its scope does not accept. */
public class InvalidPermissionsException extends RuntimeException {
    public InvalidPermissionsException(String message) {
        super(message);
    }
}
