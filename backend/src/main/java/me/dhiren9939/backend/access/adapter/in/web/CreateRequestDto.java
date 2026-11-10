package me.dhiren9939.backend.access.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.dhiren9939.backend.access.domain.Lifetime;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;

import java.util.Map;

/**
 * {@code name} and {@code expiry} are required. {@code permissions} is a JSON object keyed by scope name;
 * a missing scope means NONE.
 */
public record CreateRequestDto(@NotBlank @Size(max = 256) String name,
                               @NotNull Lifetime expiry,
                               Map<Scope, AccessLevel> permissions) {
}
