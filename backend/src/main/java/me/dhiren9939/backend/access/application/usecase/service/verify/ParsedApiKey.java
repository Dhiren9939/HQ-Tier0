package me.dhiren9939.backend.access.application.usecase.service.verify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A presented credential with the key format read from its Access ID prefix ({@code V1_...} is format {@code V1}).
 * Immutable.
 *
 * <p>Only the prefix is read here, whatever the length of the input. What a valid credential of that format looks
 * like is the format's own rule ({@code CredentialVerifier.isWellFormed}), checked before the lookup and the hash.</p>
 */
record ParsedApiKey(String accessId, String accessSecret, String keyFormat) {

    /** {@code V}, 1 to 3 digits and an underscore, at the start of the Access ID. */
    private static final Pattern KEY_FORMAT_PREFIX = Pattern.compile("(V[0-9]{1,3})_");

    static ParsedApiKey parse(String accessId, String accessSecret) {
        if (accessId == null || accessSecret == null)
            throw ApiKeyVerificationFailure.malformed();

        Matcher prefix = KEY_FORMAT_PREFIX.matcher(accessId);
        if (!prefix.lookingAt()) // anchored at the start: reads a few characters, never the whole input
            throw ApiKeyVerificationFailure.malformed();
        return new ParsedApiKey(accessId, accessSecret, prefix.group(1));
    }

    @Override
    public String toString() {
        return "ParsedApiKey[accessId=" + LoggedIdentifier.of(accessId) + ", keyFormat=" + keyFormat
                + ", accessSecret=***]";
    }
}
