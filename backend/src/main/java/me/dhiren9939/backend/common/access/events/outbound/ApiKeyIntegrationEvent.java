package me.dhiren9939.backend.common.access.events.outbound;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Integration events Access publishes to the data plane when an API key is created, updated or deleted. Each one is
 * a full snapshot of the key at {@link #version()}, so a consumer can rebuild its projection from the latest event.
 *
 * <p>Sealed on purpose: the set of integration events is closed. Adding an event type means adding it to
 * {@code permits}, which stops every exhaustive {@code switch} over them from compiling until it handles the
 * new type. The {@link #eventType()} strings ({@code apiKey.projection.*}) are the wire contract.</p>
 */
public sealed interface ApiKeyIntegrationEvent permits ApiKeyCreatedIntegrationEvent, ApiKeyUpdatedIntegrationEvent, ApiKeyDeletedIntegrationEvent {
    String eventType();

    long version();

    /** When the aggregate registered the command. */
    Instant occurredAt();

    /** When the event was published; null until then, so it is never part of the stored outbox payload. */
    Instant emittedAt();

    UUID apiKeyId();

    UUID userId();

    String accessId();

    /**
     * The key's secret hash. Every event is a full snapshot of the key at {@link #version()}, so the data plane
     * can rebuild its projection from the latest event alone, whatever it missed before.
     */
    String accessSecretHash();

    String apiKeyName();

    /** Granted level per scope, e.g. {@code {"INGESTION":"WRITE"}}; an absent scope means no access (NONE). */
    Map<ApiKeyScope, ApiKeyAccessLevel> permissions();

    Instant createdAt();

    Instant expiresAt();
}
