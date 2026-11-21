package me.dhiren9939.backend.access.presentation.api.dtos;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;

import java.util.Map;

/**
 * {@code name} and {@code expiry} are required. {@code permissions} is a JSON object keyed by scope name;
 * a missing scope means NONE, and a level cannot be null.
 *
 * <p>Every constraint's message is a key in {@code ValidationMessages.properties}; the key is the machine-readable
 * code the client receives.</p>
 */
public record CreateRequestDto(@NotBlank(message = "{NAME_REQUIRED}")
                               @Size(max = ApiKey.MAX_NAME_LENGTH, message = "{NAME_TOO_LONG}")
                               @Pattern(regexp = RequestPatterns.NO_CONTROL_CHARACTERS, message = "{NAME_INVALID_CHARACTERS}")
                               String name,
                               @NotNull(message = "{EXPIRY_REQUIRED}") Lifetime expiry,
                               Map<@NotNull(message = "{PERMISSION_SCOPE_REQUIRED}") Scope,
                                       @NotNull(message = "{PERMISSION_LEVEL_REQUIRED}") AccessLevel> permissions) {
}
