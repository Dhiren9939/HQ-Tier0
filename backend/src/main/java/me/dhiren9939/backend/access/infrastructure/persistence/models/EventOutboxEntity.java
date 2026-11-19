package me.dhiren9939.backend.access.infrastructure.persistence.models;

import jakarta.persistence.*;
import lombok.*;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A row of the outbox: the metadata a relay needs to lock, order, route and track the row, and the integration
 * event itself as a ready-to-publish JSON {@link #payload}. New events and fields change the payload, not this
 * table. Anything that must be encoded or encrypted is done by the mapper and written by the serializer before the
 * row is built.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "outbox", schema = "access")
public class EventOutboxEntity {

    /** Assigned by the caller, because the payload carries it too. */
    @Id
    private UUID eventId;

    @Column(nullable = false, length = 256)
    private String eventType;

    @Column(nullable = false)
    private UUID apiKeyId;

    @Column(name = "change_version", nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant occurredAt;

    /** Null until a relay publishes the row; the payload then holds the same time as {@code emittedAt}. */
    private Instant emittedAt;

    /** The message body. Everything except {@code emittedAt} is final when the row is written. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    /** Publish attempts the broker refused for this row; transient outages do not count. */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int attempts = 0;

    /** Why the last refused attempt failed; null until one does. */
    @Column(columnDefinition = "text")
    private String lastError;

    /** The metadata columns are copied from the event; {@code payload} is that same event as JSON. */
    public static EventOutboxEntity of(UUID eventId, ApiKeyIntegrationEvent event, String payload) {
        return EventOutboxEntity.builder()
                .eventId(eventId)
                .eventType(event.eventType())
                .apiKeyId(event.apiKeyId())
                .version(event.version())
                .occurredAt(event.occurredAt())
                .payload(payload)
                .build();
    }

    /** Records the publish: the column and the payload carry the same time, and the payload is what was sent. */
    public void emitted(Instant at, String publishedPayload) {
        this.emittedAt = at;
        this.payload = publishedPayload;
    }
}
