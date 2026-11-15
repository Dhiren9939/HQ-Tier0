package me.dhiren9939.backend.access.presentation.api.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;

import java.util.Map;

/**
 * Every field is optional; an omitted field means "leave unchanged". A given {@code name} must not be blank. In
 * {@code permissions} only the listed scopes change, and a null level also means "leave unchanged".
 */
public record UpdateRequestDto(@Size(max = 256, message = "{NAME_TOO_LONG}")
                               @Pattern(regexp = RequestPatterns.NOT_BLANK_TEXT, message = "{NAME_BLANK}")
                               @Pattern(regexp = RequestPatterns.NO_CONTROL_CHARACTERS, message = "{NAME_INVALID_CHARACTERS}")
                               String name,
                               Lifetime expiry,
                               Map<@NotNull(message = "{PERMISSION_SCOPE_REQUIRED}") Scope, AccessLevel> permissions) {
}
