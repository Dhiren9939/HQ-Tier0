package me.dhiren9939.backend.common.access.events.outbound;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Commands that tell the data plane to create, update or delete its projection of an API key.
 *
 * <p>Sealed on purpose: the set of projection commands is closed. Adding a command type means adding it to
 * {@code permits}, which stops every exhaustive {@code switch} over events (such as the outbox listener's)
 * from compiling until it handles the new type.</p>
 */
public sealed interface ApiKeyProjectionCommand permits CreateApiKeyProjectionCommand, UpdateApiKeyProjectionCommand, DeleteApiKeyProjectionCommand {
    String eventType();

    long version();

    /** When the aggregate registered the command. */
    Instant occurredAt();

    /** When the command was published into the stream; null until a relay publishes it. */
    Instant emittedAt();

    UUID apiKeyId();

    UUID userId();

    String accessId();

    String apiKeyName();

    /** Scope name to level name, e.g. {@code {"INGESTION":"WRITE"}}; an absent scope means no access. */
    Map<String, String> permissions();

    Instant createdAt();

    Instant expiresAt();
}
