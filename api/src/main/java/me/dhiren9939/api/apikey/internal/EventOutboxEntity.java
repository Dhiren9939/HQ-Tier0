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
@Table(name = "outbox")
public class EventOutboxEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID eventId;
    private String eventType;

    private UUID apiKeyId;
    private UUID userId;

    private String accessId;
    @Builder.Default
    private String accessSecretHash = null;
    private String apiKeyName;

    @Enumerated(EnumType.STRING)
    private ApiKeyPermissions.Ingestion ingestionPermission;

    @Enumerated(EnumType.STRING)
    private ApiKeyPermissions.Tenant tenantPermission;

    @Enumerated(EnumType.STRING)
    private ApiKeyPermissions.Channel channelPermission;

    private Instant createdAt;
    private Instant expiresAt;
}
