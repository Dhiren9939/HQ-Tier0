package me.dhiren9939.api.apikey.internal;

import jakarta.persistence.*;
import lombok.*;
import me.dhiren9939.api.apikey.ApiKeyPermissions;
import me.dhiren9939.api.apikey.events.ApiKeyCreated;
import me.dhiren9939.api.apikey.events.ApiKeyDeleted;
import me.dhiren9939.api.apikey.events.ApiKeyUpdated;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of an API key lifecycle event stored in the outbox.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "outbox", schema = "api_key")
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

    public static EventOutboxEntity from(ApiKeyCreated event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .userId(event.userId())
                .accessId(event.accessId())
                .accessSecretHash(event.accessSecretHash())
                .apiKeyName(event.apiKeyName())
                .ingestionPermission(event.ingestionPermission())
                .tenantPermission(event.tenantPermission())
                .channelPermission(event.channelPermission())
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .build();
    }

    public static EventOutboxEntity from(ApiKeyUpdated event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .userId(event.userId())
                .accessId(event.accessId())
                .apiKeyName(event.apiKeyName())
                .ingestionPermission(event.ingestionPermission())
                .tenantPermission(event.tenantPermission())
                .channelPermission(event.channelPermission())
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .build();
    }

    public static EventOutboxEntity from(ApiKeyDeleted event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .userId(event.userId())
                .accessId(event.accessId())
                .apiKeyName(event.apiKeyName())
                .ingestionPermission(event.ingestionPermission())
                .tenantPermission(event.tenantPermission())
                .channelPermission(event.channelPermission())
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .build();
    }
}
