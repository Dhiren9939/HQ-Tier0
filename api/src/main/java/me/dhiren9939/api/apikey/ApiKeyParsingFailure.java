package me.dhiren9939.api.apikey;

public class ApiKeyParsingFailsure extends RuntimeException {
    public ApiKeyParsingFailsure(String message) {
        super(message);
    }
}
