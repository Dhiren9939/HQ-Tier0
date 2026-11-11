package me.dhiren9939.backend.access.infrastructure.persistence.repositories;

import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data repository for API key events awaiting outbox delivery.
 */
@Repository
interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, UUID> {
}
