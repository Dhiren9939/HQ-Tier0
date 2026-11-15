package me.dhiren9939.backend.access.application.usecase.service.verify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A presented credential split into its Access ID, secret and key format. Immutable. */
record ParsedApiKey(String accessId, String accessSecret, String keyFormat) {

    private static final Pattern ACCESS_ID_PATTERN = Pattern.compile("^(V\\d+)_.+$");

    static ParsedApiKey parse(String accessId, String accessSecret) {
        if (accessId == null || accessSecret == null)
            throw ApiKeyVerificationFailure.malformed();
        Matcher matcher = ACCESS_ID_PATTERN.matcher(accessId);
        if (!matcher.matches())
            throw ApiKeyVerificationFailure.malformed();
        return new ParsedApiKey(accessId, accessSecret, matcher.group(1));
    }

    @Override
    public String toString() {
        return "ParsedApiKey[accessId=" + accessId + ", keyFormat=" + keyFormat + ", accessSecret=***]";
    }
}
