package me.dhiren9939.backend.access.infrastructure.persistence.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * An outbox row the relay gave up on, moved out of {@code access.outbox}: the same metadata and payload, plus when
 * it died. Every integration event is a full snapshot of the key, so losing one never needs a resync: the key's next
 * event carries its whole state. To replay, copy the row back into {@code access.outbox} with {@code attempts = 0}
 * (column for column, no emitted_at); consumers ignore it if they already hold a higher version.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "outbox_dead_letter", schema = "access")
public class DeadLetterEntity {

    @Id
    private UUID eventId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private UUID apiKeyId;

    @Column(name = "change_version", nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant occurredAt;

    /** The message body as it was in the outbox. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(columnDefinition = "text")
    private String lastError;

    @Column(nullable = false)
    private Instant deadAt;

    public static DeadLetterEntity of(EventOutboxEntity row, Instant deadAt) {
        return DeadLetterEntity.builder()
                .eventId(row.getEventId())
                .eventType(row.getEventType())
                .apiKeyId(row.getApiKeyId())
                .version(row.getVersion())
                .occurredAt(row.getOccurredAt())
                .payload(row.getPayload())
                .attempts(row.getAttempts())
                .lastError(row.getLastError())
                .deadAt(deadAt)
                .build();
    }
}
