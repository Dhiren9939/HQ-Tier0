package me.dhiren9939.backend.shared.access;

/** Indicates a permission level that its scope does not accept. */
public class InvalidPermissionsException extends RuntimeException {
    public InvalidPermissionsException(String message) {
        super(message);
    }
}
