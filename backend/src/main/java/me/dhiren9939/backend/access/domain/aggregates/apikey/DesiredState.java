package me.dhiren9939.backend.access.domain.aggregates.apikey;

import me.dhiren9939.backend.shared.access.Permissions;

import java.time.Instant;

/** The state an API key is heading for: the pending change if there is one, else the confirmed state. */
public record DesiredState(String apiKeyName, Permissions permissions, Instant expiresAt) {
}
