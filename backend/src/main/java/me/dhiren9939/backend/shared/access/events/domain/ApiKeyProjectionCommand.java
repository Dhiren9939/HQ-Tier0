package me.dhiren9939.backend.shared.access.events.domain;

import me.dhiren9939.backend.shared.access.Permissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Commands that tell the data plane to create, update or delete its projection of an API key.
 *
 * <p>Sealed on purpose: the set of lifecycle events is closed. Adding an event type means adding it to
 * {@code permits}, which stops every exhaustive {@code switch} over events (such as the outbox listener's)
 * from compiling until it handles the new type.</p>
 */
public sealed interface ApiKeyProjectionCommand extends ApiKeyEvent permits CreateApiKeyProjectionCommand, UpdateApiKeyProjectionCommand, DeleteApiKeyProjectionCommand {
    String apiKeyName();

    Permissions permissions();

    Instant createdAt();

}
