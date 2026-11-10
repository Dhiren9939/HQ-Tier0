package me.dhiren9939.backend.access.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.Scope;
import me.dhiren9939.backend.shared.access.events.domain.CreateApiKeyProjectionCommand;
import me.dhiren9939.backend.shared.access.events.domain.DeleteApiKeyProjectionCommand;
import me.dhiren9939.backend.shared.access.events.domain.ApiKeyChangeConfirmed;
import me.dhiren9939.backend.shared.access.events.domain.ApiKeyChangeRejected;
import me.dhiren9939.backend.shared.access.events.domain.ApiKeyEvent;
import me.dhiren9939.backend.shared.access.events.domain.UpdateApiKeyProjectionCommand;
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

    /** Projection commands only; null on change events. */
    @Column(length = 256)
    private String apiKeyName;

    /**
     * Permissions as a JSON object keyed by scope name, e.g. {@code {"INGESTION":"WRITE","TENANT":"READ"}};
     * a scope that is absent means NONE. This is the wire shape consumers read.
     */
    @Column(name = "permissions", columnDefinition = "text")
    private String permissions;

    /** Projection commands only; null on change events. */
    private Instant createdAt;

    /** Change events only: the kind of change that was confirmed or rejected. */
    @Column(length = 20)
    private String changeKind;

    /** Rejected change events only. */
    @Column(length = 50)
    private String failureReason;


    static EventOutboxEntity of(ApiKeyEvent event) {
        return switch (event) {
            case CreateApiKeyProjectionCommand created -> from(created);
            case UpdateApiKeyProjectionCommand updated -> from(updated);
            case DeleteApiKeyProjectionCommand deleted -> from(deleted);
            case ApiKeyChangeConfirmed confirmed -> from(confirmed);
            case ApiKeyChangeRejected rejected -> from(rejected);
        };
    }

    static EventOutboxEntity from(ApiKeyChangeConfirmed event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .version(event.version())
                .occurredAt(event.occurredAt())
                .emittedAt(event.emittedAt())
                .userId(event.userId())
                .accessId(event.accessId())
                .changeKind(event.changeKind().name())
                .build();
    }

    static EventOutboxEntity from(ApiKeyChangeRejected event) {
        return EventOutboxEntity.builder()
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .version(event.version())
                .occurredAt(event.occurredAt())
                .emittedAt(event.emittedAt())
                .userId(event.userId())
                .accessId(event.accessId())
                .changeKind(event.changeKind().name())
                .failureReason(event.failureReason().name())
                .build();
    }

    static EventOutboxEntity from(CreateApiKeyProjectionCommand event) {
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

    static EventOutboxEntity from(UpdateApiKeyProjectionCommand event) {
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

    static EventOutboxEntity from(DeleteApiKeyProjectionCommand event) {
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
