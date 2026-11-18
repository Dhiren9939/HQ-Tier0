package me.dhiren9939.backend.access.domain.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened to an API key and must be projected to the data plane. The aggregate registers these
 * while handling a command; the persistence adapter drains and publishes them.
 *
 * <p>Sealed on purpose: the set of events is closed. Adding an event type means adding it to {@code permits},
 * which stops every exhaustive {@code switch} over events from compiling until it handles the new type.</p>
 */
public sealed interface ApiKeyEvent permits ApiKeyCreated, ApiKeyUpdated, ApiKeyDeleted {
    UUID apiKeyId();

    /** The revision this event describes. */
    long version();

    /** When the aggregate registered the event. */
    Instant occurredAt();

    UUID userId();

    String accessId();

    /** Every event is a full snapshot of the key, so the hash travels with all of them. */
    String accessSecretHash();

    String apiKeyName();

    Permissions permissions();

    Instant createdAt();

    Instant expiresAt();

    String eventType();
}
