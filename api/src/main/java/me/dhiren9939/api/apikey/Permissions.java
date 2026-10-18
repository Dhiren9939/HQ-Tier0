package me.dhiren9939.api.apikey;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record Permissions(@Enumerated(EnumType.STRING) ApiKeyPermissions.Ingestion ingestionPermission,
                          @Enumerated(EnumType.STRING) ApiKeyPermissions.Tenant tenantPermission,
                          @Enumerated(EnumType.STRING) ApiKeyPermissions.Channel channelPermission) {
}
