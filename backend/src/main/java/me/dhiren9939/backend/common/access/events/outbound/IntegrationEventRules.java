package me.dhiren9939.backend.common.access.events.outbound;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * The rules every integration event must meet the moment it is created, so an event that breaks the published
 * contract cannot exist. They are the contract's sizes and formats; the serializer checks the written JSON against
 * the same contract. Plain Java on purpose: this package may not depend on anything else.
 */
final class IntegrationEventRules {
    /** Longest apiKeyName, accessId and accessSecretHash, in characters. */
    static final int MAX_TEXT = 256;

    private IntegrationEventRules() {
    }

    /**
     * Throws {@link IllegalArgumentException} naming the field unless the values form a valid event; returns the
     * permissions to keep, an unmodifiable copy ordered by scope.
     */
    static Map<ApiKeyScope, ApiKeyAccessLevel> check(String eventType, String apiKeyName, UUID apiKeyId, long version,
                                                     Instant occurredAt, UUID userId, String accessId,
                                                     String accessSecretHash,
                                                     Map<ApiKeyScope, ApiKeyAccessLevel> permissions,
                                                     Instant createdAt, Instant expiresAt) {
        text(eventType, "apiKeyName", apiKeyName);
        required(eventType, "apiKeyId", apiKeyId);
        if (version < 1)
            throw invalid(eventType, "version", "is " + version + ", expected at least 1");
        required(eventType, "occurredAt", occurredAt);
        required(eventType, "userId", userId);
        text(eventType, "accessId", accessId);
        text(eventType, "accessSecretHash", accessSecretHash);
        required(eventType, "createdAt", createdAt);
        if (occurredAt.isBefore(createdAt))
            throw invalid(eventType, "occurredAt", "is before createdAt");
        if (expiresAt != null && !expiresAt.isAfter(createdAt))
            throw invalid(eventType, "expiresAt", "is not after createdAt; null means the key never expires");
        return permissions(eventType, permissions);
    }

    private static Map<ApiKeyScope, ApiKeyAccessLevel> permissions(String eventType,
                                                                   Map<ApiKeyScope, ApiKeyAccessLevel> permissions) {
        required(eventType, "permissions", permissions);
        Map<ApiKeyScope, ApiKeyAccessLevel> granted = new EnumMap<>(ApiKeyScope.class);
        for (Map.Entry<ApiKeyScope, ApiKeyAccessLevel> grant : permissions.entrySet()) {
            if (grant.getKey() == null)
                throw invalid(eventType, "permissions", "has a null scope");
            if (grant.getValue() == null)
                throw invalid(eventType, "permissions", "has no level for " + grant.getKey()
                        + "; leave the scope out for no access");
            if (grant.getKey() == ApiKeyScope.INGESTION && grant.getValue() != ApiKeyAccessLevel.WRITE)
                throw invalid(eventType, "permissions", "has INGESTION set to " + grant.getValue()
                        + "; INGESTION only allows WRITE");
            granted.put(grant.getKey(), grant.getValue());
        }
        return Collections.unmodifiableMap(granted);
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
        return new IllegalArgumentException("Integration event " + eventType + ": " + field + " " + problem);
    }
}
