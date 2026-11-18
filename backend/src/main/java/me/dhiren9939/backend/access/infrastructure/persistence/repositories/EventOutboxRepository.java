package me.dhiren9939.backend.access.infrastructure.persistence.repositories;

import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for API key events awaiting outbox delivery.
 */
@Repository
interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, UUID> {

    /**
     * The oldest unpublished rows, locked for the rest of the caller's transaction. Rows another transaction already
     * holds are skipped rather than waited for.
     */
    @Query(value = """
            SELECT * FROM access.outbox
            WHERE emitted_at IS NULL
            ORDER BY occurred_at, change_version
            LIMIT :limit
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<EventOutboxEntity> lockPending(@Param("limit") int limit);
}
