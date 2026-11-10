package me.dhiren9939.backend.access.application.port.in;

import me.dhiren9939.backend.access.domain.FailureReason;

import java.util.UUID;

/** The data plane (or a timeout) rejects the pending change of the given revision. */
public record RejectChangeCommand(UUID apiKeyId, long revision, FailureReason reason) {
}
