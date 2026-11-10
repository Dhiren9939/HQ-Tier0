package me.dhiren9939.backend.shared.access.events.domain;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Fact: the data plane confirmed the change at {@code version}, so it has taken effect.
 */
@Builder
public record ApiKeyChangeConfirmed(UUID apiKeyId, long version, ChangeKind changeKind, Instant occurredAt,
                                    Instant emittedAt, UUID userId, String accessId) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.change.confirmed";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
