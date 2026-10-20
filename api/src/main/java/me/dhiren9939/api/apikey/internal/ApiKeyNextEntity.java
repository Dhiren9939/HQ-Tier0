package me.dhiren9939.api.apikey.internal;

import jakarta.persistence.*;
import lombok.*;
import me.dhiren9939.api.apikey.ApiKeyPermissions;
import me.dhiren9939.api.apikey.Permissions;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "api_key_next", schema = "api_key")
public class ApiKeyNextEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "next_id")
    private UUID nextId;

    // bookkeeping
    @Column(nullable = false)
    private long updateVersion;

    @Column(nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApiKeyUpdateType updateType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApiKeyUpdateStatus updateStatus;

    @Column(length = 50)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private ApiKeyFailureReason failureReason = null;

    // Attributes which can be updated
    private String apiKeyName;

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

    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private long version;

}
