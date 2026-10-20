package me.dhiren9939.api.apikey.internal;

public class ApiKeyUpdateFailure extends RuntimeException {

    public ApiKeyUpdateFailure(){
        super("Failed to update the API key");
    }

    public ApiKeyUpdateFailure(String message) {
        super(message);
    }
}
