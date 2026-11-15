package me.dhiren9939.backend.common.access.events.inbound;

import java.util.UUID;

/**
 * Incoming from the data plane: the downstream service named by {@code issuer} (like the {@code iss} claim of a JWT) applied the change at {@code version},
 * so that revision took effect.
 */
public record ApiKeyChangeConfirmed(UUID apiKeyId, long version, String issuer) {
}
