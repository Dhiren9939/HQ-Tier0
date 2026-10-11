package me.dhiren9939.api.apikey.internal;

import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.apikey.ApiKeyPermissions;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class ApiKey {
    private String accessId;
    private String accessSecret;
    private String apiKeyName;

    private String keyVersion;
    private boolean verified;

    private UUID userId;
    private UUID apiKeyId;

    private ApiKeyPermissions.Ingestion ingestionPermission;
    private ApiKeyPermissions.Tenant tenantPermission;
    private ApiKeyPermissions.Channel channelPermission;

    private Instant createdAt;
    private Instant expiresAt;

    ApiKey(String accessId, String accessSecret, String keyVersion){
        this.accessId = accessId;
        this.accessSecret = accessSecret;
        this.keyVersion = keyVersion;
    }
}
