package me.dhiren9939.backend.access.infrastructure.persistence.repositories;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.infrastructure.persistence.models.DeadLetterEntity;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * What the outbox relay may do with the outbox. Every method must run inside the relay's transaction: the rows
 * {@link #lockPending(int)} returns stay locked, and the other methods change those managed rows in place, so the
 * changes are written when the transaction commits.
 */
@Component
@RequiredArgsConstructor
public class OutboxStore {
    private final EventOutboxRepository outbox;
    private final DeadLetterRepository deadLetters;

    /** Up to {@code limit} unpublished rows, oldest first, skipping rows another transaction holds. */
    public List<EventOutboxEntity> lockPending(int limit) {
        return outbox.lockPending(limit);
    }

    /** Stamps rows the broker confirmed. */
    public void markEmitted(List<EventOutboxEntity> rows, Instant at) {
        rows.forEach(row -> row.setEmittedAt(at));
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
