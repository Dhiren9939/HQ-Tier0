package me.dhiren9939.backend.access.presentation.api.dtos;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;

import java.util.Map;

/**
 * Every field is optional; an omitted field means "leave unchanged". A given {@code name} must not be blank. In
 * {@code permissions} only the listed scopes change, and a null level also means "leave unchanged".
 */
public record UpdateRequestDto(@Size(max = 256)
                               @Pattern(regexp = RequestPatterns.NOT_BLANK_TEXT, message = "must not be blank")
                               @Pattern(regexp = RequestPatterns.NO_CONTROL_CHARACTERS, message = "must not contain control characters")
                               String name,
                               Lifetime expiry,
                               Map<@jakarta.validation.constraints.NotNull Scope, AccessLevel> permissions) {
}
