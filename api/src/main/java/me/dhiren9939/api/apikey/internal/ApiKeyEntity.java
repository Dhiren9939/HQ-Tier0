package me.dhiren9939.api.apikey.internal;

import jakarta.persistence.*;
import lombok.*;
import me.dhiren9939.api.apikey.ApiKeyPermissions;
import me.dhiren9939.api.apikey.Permissions;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of a stored API key and its owner, permissions, and expiry.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "api_keys", schema = "api_key")
public class ApiKeyEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID apiKeyId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 256)
    private String accessId;

    @Column(nullable = false, length = 256)
    private String accessSecretHash;

    @Column(nullable = false, length = 256)
    private String apiKeyName;

    @Builder.Default
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ingestionPermission",
                    column = @Column(name = "ingestion_permission", nullable = false, length = 50)),
            @AttributeOverride(name = "tenantPermission",
                    column = @Column(name = "tenant_permission", nullable = false, length = 50)),
            @AttributeOverride(name = "channelPermission",
                    column = @Column(name = "channel_permission", nullable = false, length = 50))
    })
    private Permissions permissions = new Permissions(
            ApiKeyPermissions.Ingestion.NONE,
            ApiKeyPermissions.Tenant.NONE,
            ApiKeyPermissions.Channel.NONE
    );

    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

}
