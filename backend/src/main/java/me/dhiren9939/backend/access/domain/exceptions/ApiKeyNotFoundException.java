package me.dhiren9939.backend.access.domain.exceptions;

public class ApiKeyNotFoundException extends RuntimeException {
    public ApiKeyNotFoundException() {
        super("API key not found");
    }

    public ApiKeyNotFoundException(String message) {
        super(message);
    }
}
