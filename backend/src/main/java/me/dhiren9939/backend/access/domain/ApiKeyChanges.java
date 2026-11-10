package me.dhiren9939.backend.access.domain;

/** Requested update to an API key. Every field is optional; {@code null} means "leave unchanged". */
public record ApiKeyChanges(String name, Lifetime lifetime, PermissionChanges permissions) {
}
