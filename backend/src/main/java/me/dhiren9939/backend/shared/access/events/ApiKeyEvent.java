package me.dhiren9939.backend.shared.access.events;

import me.dhiren9939.backend.shared.access.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Common event contract for API key creation, update, and deletion events.
 *
 * <p>Sealed on purpose: the set of lifecycle events is closed. Adding an event type means adding it to
 * {@code permits}, which stops every exhaustive {@code switch} over events (such as the outbox listener's)
 * from compiling until it handles the new type.</p>
 */
public sealed interface ApiKeyEvent permits ApiKeyCreated, ApiKeyUpdated, ApiKeyDeleted {
    String eventType();

    long version();

    /** When the change reached the outbox (set by the aggregate). */
    Instant occurredAt();

    /** When the event was published into the stream; null until a relay publishes it. */
    Instant emittedAt();

    String apiKeyName();

    UUID apiKeyId();

    UUID userId();

    String accessId();

    Permissions permissions();

    Instant createdAt();

}
