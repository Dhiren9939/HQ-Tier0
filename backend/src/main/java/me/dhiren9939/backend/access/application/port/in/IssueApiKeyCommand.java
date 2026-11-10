package me.dhiren9939.backend.access.application.port.in;

import me.dhiren9939.backend.access.domain.Lifetime;
import me.dhiren9939.backend.shared.access.Permissions;

import java.util.UUID;

/** Request to issue a new API key. {@code permissions} must be complete (no null levels). */
public record IssueApiKeyCommand(UUID userId, String name, Lifetime lifetime, Permissions permissions) {
}
