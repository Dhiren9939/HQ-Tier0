package me.dhiren9939.backend.access.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data repository for API key events awaiting outbox delivery.
 */
@Repository
interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, UUID> {
}
