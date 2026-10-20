package me.dhiren9939.api.apikey.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Persistence access for proposed API-key states awaiting downstream resolution. */
@Repository
interface ApiKeyNextRepository extends JpaRepository<ApiKeyNextEntity, UUID> {
}
