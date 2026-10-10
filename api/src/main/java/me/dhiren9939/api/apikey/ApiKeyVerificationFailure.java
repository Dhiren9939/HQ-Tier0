package me.dhiren9939.api.apikey;

public class ApiKeyVerificationFailure extends RuntimeException {
    public ApiKeyVerificationFailure(String message) {
        super(message);
    }
}
