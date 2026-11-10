package me.dhiren9939.backend.access.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.shared.access.events.ApiKeyCreated;
import me.dhiren9939.backend.shared.access.events.ApiKeyDeleted;
import me.dhiren9939.backend.shared.access.events.ApiKeyEvent;
import me.dhiren9939.backend.shared.access.events.ApiKeyUpdated;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes every API key lifecycle event to the transactional outbox just before the surrounding
 * transaction commits. The outbox row is saved in the same transaction as the key change, so if it
 * cannot be written the key change rolls back too.
 *
 * <p>Events published outside a transaction are not delivered here; every command runs in one.</p>
 */
@Component
@RequiredArgsConstructor
class OutboxEventListener {
    private final EventOutboxRepository outboxRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void handle(ApiKeyEvent event) {
        outboxRepository.save(toRow(event));
    }

    private static EventOutboxEntity toRow(ApiKeyEvent event) {
        return switch (event) {
            case ApiKeyCreated created -> EventOutboxEntity.from(created);
            case ApiKeyUpdated updated -> EventOutboxEntity.from(updated);
            case ApiKeyDeleted deleted -> EventOutboxEntity.from(deleted);
        };
    }
}
