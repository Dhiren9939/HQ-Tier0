package me.dhiren9939.backend.access.infrastructure.persistence.repositories;

import me.dhiren9939.backend.access.infrastructure.persistence.models.DeadLetterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Outbox rows the relay gave up on. */
@Repository
interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, UUID> {
}
