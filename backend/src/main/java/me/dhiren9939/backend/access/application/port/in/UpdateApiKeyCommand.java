package me.dhiren9939.backend.access.application.port.in;

import me.dhiren9939.backend.access.domain.Lifetime;
import me.dhiren9939.backend.access.domain.PermissionChanges;

import java.util.UUID;

/** Request to change a key. Every change field is optional; {@code null} means "leave unchanged". */
public record UpdateApiKeyCommand(UUID userId, UUID apiKeyId, String name, Lifetime lifetime,
                                  PermissionChanges permissions) {
}
