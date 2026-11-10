package me.dhiren9939.backend.access.application.port.in;

/** A credential as presented by a caller. */
public record VerifyApiKeyQuery(String accessId, String accessSecret) {

    @Override
    public String toString() {
        return "VerifyApiKeyQuery[accessId=" + accessId + ", accessSecret=***]";
    }
}
