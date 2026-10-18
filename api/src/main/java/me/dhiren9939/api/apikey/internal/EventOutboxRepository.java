package me.dhiren9939.api.apikey.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data repository for API key events awaiting outbox delivery.
 */
@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, UUID> {
}
