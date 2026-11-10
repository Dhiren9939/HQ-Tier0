package me.dhiren9939.backend.access.application.port.in;

import java.util.UUID;

/** The data plane confirms the pending change of the given revision. */
public record ConfirmChangeCommand(UUID apiKeyId, long revision) {
}
