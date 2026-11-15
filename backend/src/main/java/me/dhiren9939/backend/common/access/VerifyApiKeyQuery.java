package me.dhiren9939.backend.common.access;

public record VerifyApiKeyQuery(String accessId, String accessSecret) {

    @Override
    public String toString() {
        return "VerifyApiKeyQuery[accessId=" + accessId + ", accessSecret=***]";
    }
}

