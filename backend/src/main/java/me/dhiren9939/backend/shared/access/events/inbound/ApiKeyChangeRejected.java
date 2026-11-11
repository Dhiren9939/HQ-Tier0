package me.dhiren9939.backend.shared.access.events.inbound;

import java.util.UUID;

/**
 * Incoming from the data plane: the change at {@code version} failed for {@code failureReason}.
 */
public record ApiKeyChangeRejected(UUID apiKeyId, long version, FailureReason failureReason) {
}
