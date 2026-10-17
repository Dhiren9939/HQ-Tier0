package me.dhiren9939.api.apikey.internal;

import jakarta.persistence.*;
import lombok.*;
import me.dhiren9939.api.apikey.ApiKeyPermissions;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

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
    private UUID userId;

    @Column(unique = true)
    private String accessId;
    private String accessSecretHash;
    private String apiKeyName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private ApiKeyPermissions.Ingestion ingestionPermission = ApiKeyPermissions.Ingestion.NONE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private ApiKeyPermissions.Tenant tenantPermission = ApiKeyPermissions.Tenant.NONE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private ApiKeyPermissions.Channel channelPermission = ApiKeyPermissions.Channel.NONE;

    private Instant createdAt;
    private Instant expiresAt;
}
