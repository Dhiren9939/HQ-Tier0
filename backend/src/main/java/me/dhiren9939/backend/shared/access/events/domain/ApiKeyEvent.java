package me.dhiren9939.backend.shared.access.events.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything Access writes to its outbox: the projection commands that tell the data plane what a key must
 * look like, and the facts about how a requested change ended.
 *
 * <p>Sealed on purpose: the set is closed. Adding a type means adding it to {@code permits}, which stops every
 * exhaustive {@code switch} over events (such as the outbox mapping) from compiling until it handles it.</p>
 */
public sealed interface ApiKeyEvent permits ApiKeyProjectionCommand, ApiKeyChangeConfirmed, ApiKeyChangeRejected {
    String eventType();

    /** The revision this event is about. */
    long version();

    /** When the aggregate registered the event. */
    Instant occurredAt();

    /** When the event was published into the stream; null until a relay publishes it. */
    Instant emittedAt();

    UUID apiKeyId();

    UUID userId();

    String accessId();
}
