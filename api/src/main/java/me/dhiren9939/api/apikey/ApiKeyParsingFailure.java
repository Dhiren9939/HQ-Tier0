package me.dhiren9939.api.apikey;

/**
 * Indicates that an API key credential could not be parsed from its supplied format.
 */
public class ApiKeyParsingFailure extends RuntimeException {
    public ApiKeyParsingFailure(String message) {
        super(message);
    }
}
