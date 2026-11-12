package me.dhiren9939.backend.access.presentation.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;

import java.util.Map;

/**
 * {@code name} and {@code expiry} are required. {@code permissions} is a JSON object keyed by scope name;
 * a missing scope means NONE, and a level cannot be null.
 */
public record CreateRequestDto(@NotBlank @Size(max = 256)
                               @Pattern(regexp = RequestPatterns.NO_CONTROL_CHARACTERS, message = "must not contain control characters")
                               String name,
                               @NotNull Lifetime expiry,
                               Map<@NotNull Scope, @NotNull AccessLevel> permissions) {
}
