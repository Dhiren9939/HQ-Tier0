package me.dhiren9939.backend.shared.access.events.inbound;

import java.util.UUID;

/**
 * Incoming from the data plane: the downstream service named by {@code issuer} (like the {@code iss} claim of a JWT) could not apply the change at
 * {@code version}, for {@code failureReason}.
 */
public record ApiKeyChangeRejected(UUID apiKeyId, long version, String issuer, FailureReason failureReason) {
}
