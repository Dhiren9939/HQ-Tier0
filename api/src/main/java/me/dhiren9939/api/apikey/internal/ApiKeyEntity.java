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

     static final int NON_EXISTENT = 0;

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID apiKeyId;

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

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "next_id")
    private ApiKeyNextEntity next;

    @Builder.Default
    @Column(nullable = false)
    private long appliedVersion = 0;

    @Builder.Default
    @Column(nullable = false)
    private long issuedVersion = 1;

    @Builder.Default
    @Column(nullable = false)
    private long desiredVersion = 1;

    @Version
    @Column(nullable = false)
    private long version;
}
