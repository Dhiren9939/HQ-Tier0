package me.dhiren9939.backend.access.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.Scope;
import me.dhiren9939.backend.shared.access.events.ApiKeyCreated;
import me.dhiren9939.backend.shared.access.events.ApiKeyDeleted;
import me.dhiren9939.backend.shared.access.events.ApiKeyUpdated;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Map;
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
@Table(name = "outbox", schema = "access")
class EventOutboxEntity {

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
    private Instant occurredAt;

    /** Null until a relay publishes the row into the stream. */
    private Instant emittedAt;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 256)
    private String accessId;

    @Builder.Default
    private String accessSecretHash = null;

    @Column(nullable = false, length = 256)
    private String apiKeyName;

    /**
     * Permissions as a JSON object keyed by scope name, e.g. {@code {"INGESTION":"WRITE","TENANT":"READ"}};
     * a scope that is absent means NONE. This is the wire shape consumers read.
     */
    @Column(name = "permissions", nullable = false, columnDefinition = "text")
    private String permissions;

    @Column(nullable = false)
    private Instant createdAt;


    static EventOutboxEntity from(ApiKeyCreated event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .version(event.version())
                .occurredAt(event.occurredAt())
                .emittedAt(event.emittedAt())
                .userId(event.userId())
                .accessId(event.accessId())
                .accessSecretHash(event.accessSecretHash())
                .apiKeyName(event.apiKeyName())
                .permissions(permissionsJson(event.permissions()))
                .createdAt(event.createdAt())
                .build();
    }

    static EventOutboxEntity from(ApiKeyUpdated event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .version(event.version())
                .occurredAt(event.occurredAt())
                .emittedAt(event.emittedAt())
                .userId(event.userId())
                .accessId(event.accessId())
                .apiKeyName(event.apiKeyName())
                .permissions(permissionsJson(event.permissions()))
                .createdAt(event.createdAt())
                .build();
    }

    static EventOutboxEntity from(ApiKeyDeleted event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .version(event.version())
                .occurredAt(event.occurredAt())
                .emittedAt(event.emittedAt())
                .userId(event.userId())
                .accessId(event.accessId())
                .apiKeyName(event.apiKeyName())
                .permissions(permissionsJson(event.permissions()))
                .createdAt(event.createdAt())
                .build();
    }

    /** Serialises explicitly (scope and level names only contain letters and underscores, so no escaping). */
    static String permissionsJson(Permissions permissions) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Scope, AccessLevel> entry : permissions.asMap().entrySet()) {
            if (!first)
                json.append(',');
            json.append('"').append(entry.getKey().name()).append("\":\"").append(entry.getValue().name()).append('"');
            first = false;
        }
        return json.append('}').toString();
    }
}
