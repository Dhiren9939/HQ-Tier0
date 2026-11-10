package me.dhiren9939.backend.shared.access.events.domain;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Fact: the change at {@code version} failed, for whatever {@code failureReason}: the data plane rejected it,
 * it was not confirmed in time, it could not be published, or the owner cancelled it. The compensating
 * projection command follows it in the stream.
 */
@Builder
public record ApiKeyChangeRejected(UUID apiKeyId, long version, ChangeKind changeKind, FailureReason failureReason,
                                   Instant occurredAt, Instant emittedAt, UUID userId,
                                   String accessId) implements ApiKeyEvent {

    public static final String EVENT_TYPE = "apiKey.change.rejected";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
