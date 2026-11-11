package me.dhiren9939.backend.shared.access.events.inbound;

import java.util.UUID;

/**
 * Incoming from the data plane: it applied the change at {@code version}, so that revision took effect.
 */
public record ApiKeyChangeConfirmed(UUID apiKeyId, long version) {
}
