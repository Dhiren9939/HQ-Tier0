package me.dhiren9939.api.apikey;

public class ApiKeyParsingFailure extends RuntimeException {
    public ApiKeyParsingFailure(String message) {
        super(message);
    }
}
