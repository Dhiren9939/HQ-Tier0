package me.dhiren9939.api.apikey.internal;

import jakarta.persistence.*;
import lombok.*;
import me.dhiren9939.api.apikey.Permissions;
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

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private UUID apiKeyId;

    @Column(name = "change_version", nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant emittedAt;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 256)
    private String accessId;

    @Builder.Default
    private String accessSecretHash = null;

    @Column(nullable = false, length = 256)
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
    private Permissions permissions;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

    public static EventOutboxEntity from(ApiKeyCreated event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .version(event.version())
                .emittedAt(event.emittedAt())
                .userId(event.userId())
                .accessId(event.accessId())
                .accessSecretHash(event.accessSecretHash())
                .apiKeyName(event.apiKeyName())
                .permissions(event.permissions())
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .build();
    }

    public static EventOutboxEntity from(ApiKeyUpdated event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .version(event.version())
                .emittedAt(event.emittedAt())
                .userId(event.userId())
                .accessId(event.accessId())
                .apiKeyName(event.apiKeyName())
                .permissions(event.permissions())
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .build();
    }

    public static EventOutboxEntity from(ApiKeyDeleted event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .version(event.version())
                .emittedAt(event.emittedAt())
                .userId(event.userId())
                .accessId(event.accessId())
                .apiKeyName(event.apiKeyName())
                .permissions(event.permissions())
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .build();
    }
}
