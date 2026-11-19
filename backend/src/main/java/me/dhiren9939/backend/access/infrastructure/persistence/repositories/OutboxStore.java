package me.dhiren9939.backend.access.infrastructure.persistence.repositories;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.infrastructure.persistence.models.DeadLetterEntity;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import me.dhiren9939.backend.access.infrastructure.serialization.IntegrationEventSerializer;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The outbox. The persistence adapter {@link #append appends} events in the transaction that changes the key. A relay
 * locks, stamps, fails and dead-letters rows: those methods must run inside the relay's transaction, because the rows
 * {@link #lockPending(int)} returns stay locked and the others change those managed rows in place, so the changes are
 * written when the transaction commits.
 */
@Component
@RequiredArgsConstructor
public class OutboxStore {
    private final EventOutboxRepository outbox;
    private final DeadLetterRepository deadLetters;
    private final IntegrationEventSerializer serializer;

    /**
     * Writes the event as a new row. It joins the caller's transaction on purpose: a serialization failure is
     * unchecked, so it rolls back the key change that produced the event.
     */
    public void append(ApiKeyIntegrationEvent event) {
        UUID eventId = UuidCreator.getTimeOrderedEpoch();
        outbox.save(EventOutboxEntity.of(eventId, event, serializer.toJson(eventId, event)));
    }

    /** Up to {@code limit} unpublished rows, oldest first, skipping rows another transaction holds. */
    public List<EventOutboxEntity> lockPending(int limit) {
        return outbox.lockPending(limit);
    }

    /** Stamps rows the broker confirmed, in the column and in the stored payload. */
    public void markEmitted(List<EventOutboxEntity> rows, Instant at) {
        rows.forEach(row -> row.emitted(at, serializer.withEmittedAt(row.getPayload(), at)));
    }

    /** Records a refused attempt and returns how many there have been. */
    public int recordFailure(EventOutboxEntity row, String error) {
        row.setAttempts(row.getAttempts() + 1);
        row.setLastError(error);
        return row.getAttempts();
    }

    /** Moves the row out of the outbox into the dead-letter table, in the same transaction. */
    public void deadLetter(EventOutboxEntity row, String error, Instant at) {
        row.setLastError(error);
        deadLetters.save(DeadLetterEntity.of(row, at));
        outbox.delete(row);
    }
}
