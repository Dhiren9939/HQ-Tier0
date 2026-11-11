package me.dhiren9939.backend.access.presentation.api.dtos;

import jakarta.validation.constraints.Size;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;

import java.util.Map;

/**
 * Every field is optional; an omitted field means "leave unchanged". In {@code permissions} only the
 * listed scopes change.
 */
public record UpdateRequestDto(@Size(min = 1, max = 256) String name, Lifetime expiry,
                               Map<Scope, AccessLevel> permissions) {
}
