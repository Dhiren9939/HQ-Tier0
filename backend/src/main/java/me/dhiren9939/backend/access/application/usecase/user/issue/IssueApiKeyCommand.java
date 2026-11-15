package me.dhiren9939.backend.access.application.usecase.user.issue;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;

import java.util.UUID;

/** Request to issue a new API key. {@code permissions} must be complete (no null levels). */
public record IssueApiKeyCommand(UUID userId, String name, Lifetime lifetime, Permissions permissions) {
}
