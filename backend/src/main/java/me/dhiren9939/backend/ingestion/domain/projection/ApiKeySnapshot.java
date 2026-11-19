package me.dhiren9939.backend.ingestion.domain.projection;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One version of an API key, as Access published it, in Ingestion's terms. Only what Ingestion needs to authenticate
 * a caller is kept. Every snapshot is the key's full state at {@code version}, so the latest one alone is enough.
 *
 * <p>Ingestion keeps its own copy of the rules (no shared logic with Access): nothing is null, text is non-blank and
 * at most {@value #MAX_TEXT} characters, {@code version} is at least 1. An empty {@code expiresAt} means the key never
 * expires.</p>
 */
public record ApiKeySnapshot(UUID apiKeyId, long version, KeyStatus status, UUID userId, String accessId,
                             String accessSecretHash, IngestionAccess ingestionAccess, Optional<Instant> expiresAt) {

    /** Longest accessId and accessSecretHash, in characters. */
    public static final int MAX_TEXT = 256;

    /** Refuses, naming the field, anything that is not a valid snapshot. */
    public ApiKeySnapshot {
        required("apiKeyId", apiKeyId);
        if (version < 1)
            throw invalid("version", "is " + version + ", expected at least 1");
        required("status", status);
        required("userId", userId);
        text("accessId", accessId);
        text("accessSecretHash", accessSecretHash);
        required("ingestionAccess", ingestionAccess);
        required("expiresAt", expiresAt);
    }

    private static void text(String field, String value) {
        required(field, value);
        if (value.isBlank())
            throw invalid(field, "is blank");
        if (value.length() > MAX_TEXT)
            throw invalid(field, "is longer than " + MAX_TEXT + " characters");
    }

    private static void required(String field, Object value) {
        if (value == null)
            throw invalid(field, "is required");
    }

    private static IllegalArgumentException invalid(String field, String problem) {
        return new IllegalArgumentException("API key snapshot: " + field + " " + problem);
    }
}
