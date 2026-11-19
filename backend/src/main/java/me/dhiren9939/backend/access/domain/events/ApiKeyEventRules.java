package me.dhiren9939.backend.access.domain.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * What every domain event must satisfy when the aggregate registers it, so the events handed to the persistence
 * layer are always complete and within the sizes the key's columns allow. The domain keeps its own copy of these
 * rules; the published contract has its own.
 */
final class ApiKeyEventRules {
    /** Longest apiKeyName, accessId and accessSecretHash, in characters (the key's column length). */
    static final int MAX_TEXT = 256;

    private ApiKeyEventRules() {
    }

    /** Throws {@link IllegalArgumentException} naming the field unless the values form a valid event. */
    static void check(String eventType, String apiKeyName, UUID apiKeyId, long version, Instant occurredAt,
                      UUID userId, String accessId, String accessSecretHash, Permissions permissions,
                      Instant createdAt, Instant expiresAt) {
        text(eventType, "apiKeyName", apiKeyName);
        required(eventType, "apiKeyId", apiKeyId);
        if (version < 1)
            throw invalid(eventType, "version", "is " + version + ", expected at least 1");
        required(eventType, "occurredAt", occurredAt);
        required(eventType, "userId", userId);
        text(eventType, "accessId", accessId);
        text(eventType, "accessSecretHash", accessSecretHash);
        required(eventType, "permissions", permissions);
        required(eventType, "createdAt", createdAt);
        if (occurredAt.isBefore(createdAt))
            throw invalid(eventType, "occurredAt", "is before createdAt");
        if (expiresAt != null && !expiresAt.isAfter(createdAt))
            throw invalid(eventType, "expiresAt", "is not after createdAt; null means the key never expires");
    }

    private static void text(String eventType, String field, String value) {
        required(eventType, field, value);
        if (value.isBlank())
            throw invalid(eventType, field, "is blank");
        if (value.length() > MAX_TEXT)
            throw invalid(eventType, field, "is longer than " + MAX_TEXT + " characters");
    }

    private static void required(String eventType, String field, Object value) {
        if (value == null)
            throw invalid(eventType, field, "is required");
    }

    private static IllegalArgumentException invalid(String eventType, String field, String problem) {
        return new IllegalArgumentException("Domain event " + eventType + ": " + field + " " + problem);
    }
}
