package me.dhiren9939.backend.shared.access.events.inbound;

import java.util.UUID;

/**
 * Incoming from the data plane: the service named by {@code serviceId} could not apply the change at
 * {@code version}, for {@code failureReason}.
 */
public record ApiKeyChangeRejected(UUID apiKeyId, long version, String serviceId, FailureReason failureReason) {
}
