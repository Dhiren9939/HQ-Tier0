package me.dhiren9939.api.apikey.internal;

import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Holds a parsed API key while it is being verified and, after verification,
 * its associated identity, permissions, and lifecycle timestamps.
 *
 * <p>{@code accessSecret} is the secret presented by the caller; persisted
 * API keys store a hash instead. Treat this object as sensitive and do not log
 * its secret.</p>
 */
@Getter
@Setter
public class ApiKeyToken {
    /** Public identifier supplied with the API key. */
    private String accessId;
    /** Secret supplied by the caller. Sensitive; do not log. */
    private String accessSecret;
    /** User-facing name assigned to the API key. Populated after verification. */
    private String apiKeyName;

    /** Version of the key format, used to select a verification provider. */
    private String keyVersion;
    /** Whether a verification provider successfully authenticated this key. */
    private boolean verified;

    /** Owner of the API key. Populated after verification. */
    private UUID userId;
    /** Identifier of the API key record. Populated after verification. */
    private UUID apiKeyId;

    /** Allowed operations, grouped by ingestion, tenant, and channel. Populated after verification. */
    private Permissions permissions;

    /** Time the API key was created. Populated after verification. */
    private Instant createdAt;
    /** Expiration time, or {@code null} when the key does not expire. */
    private Instant expiresAt;

    /** Creates a parsed key from its identifier, secret, and format version. */
    ApiKeyToken(String accessId, String accessSecret, String keyVersion){
        this.accessId = accessId;
        this.accessSecret = accessSecret;
        this.keyVersion = keyVersion;
    }
}
