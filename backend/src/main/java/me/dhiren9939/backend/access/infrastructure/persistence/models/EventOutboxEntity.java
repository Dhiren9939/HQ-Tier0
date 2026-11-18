package me.dhiren9939.backend.access.infrastructure.persistence.models;

import jakarta.persistence.*;
import lombok.*;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA representation of an outgoing API key integration event stored in the outbox. It stores the event's fields
 * as the mapper produced them; any encoding or encryption happens in the mapper, before the row is built.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "outbox", schema = "access")
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
    private Instant occurredAt;

    /** Null until a relay publishes the row into the stream. */
    private Instant emittedAt;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 256)
    private String accessId;

    /** Filled on every row written since events became full snapshots; older rows may hold null. */
    @Column(length = 256)
    private String accessSecretHash;

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

    private Instant expiresAt;

    /** Every integration event is a full snapshot of the key, so all of them map the same way. */
    public static EventOutboxEntity of(ApiKeyIntegrationEvent event) {
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
                .expiresAt(event.expiresAt())
                .build();
    }

    /** Serialises explicitly (scope and level names only contain letters and underscores, so no escaping). */
    static String permissionsJson(Map<String, String> permissions) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : permissions.entrySet()) {
            if (!first)
                json.append(',');
            json.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
            first = false;
        }
        return json.append('}').toString();
    }
}
