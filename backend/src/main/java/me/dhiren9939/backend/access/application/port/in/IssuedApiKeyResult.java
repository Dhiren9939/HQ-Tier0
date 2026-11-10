package me.dhiren9939.backend.access.application.port.in;

/** Outcome of issuing a key. The only type that ever carries the secret, which is shown exactly once. */
public record IssuedApiKeyResult(ApiKeyView key, String accessSecret) {

    @Override
    public String toString() {
        return "IssuedApiKeyResult[key=" + key + ", accessSecret=***]";
    }
}
