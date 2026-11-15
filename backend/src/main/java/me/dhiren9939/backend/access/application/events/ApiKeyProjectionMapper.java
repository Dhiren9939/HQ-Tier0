package me.dhiren9939.backend.access.application.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.events.ApiKeyCreated;
import me.dhiren9939.backend.access.domain.events.ApiKeyDeleted;
import me.dhiren9939.backend.access.domain.events.ApiKeyEvent;
import me.dhiren9939.backend.access.domain.events.ApiKeyUpdated;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyProjectionCommand;
import me.dhiren9939.backend.common.access.events.outbound.CreateApiKeyProjectionCommand;
import me.dhiren9939.backend.common.access.events.outbound.DeleteApiKeyProjectionCommand;
import me.dhiren9939.backend.common.access.events.outbound.UpdateApiKeyProjectionCommand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates the Access domain's events into the projection commands published to the data plane. This is the
 * only place where the domain model and the common contract meet, which keeps {@code domain} independent of
 * {@code common} and {@code common} free of domain types.
 */
public final class ApiKeyProjectionMapper {

    private ApiKeyProjectionMapper() {
    }

    public static ApiKeyProjectionCommand toProjectionCommand(ApiKeyEvent event) {
        return switch (event) {
            case ApiKeyCreated created -> CreateApiKeyProjectionCommand.builder()
                    .apiKeyName(created.apiKeyName())
                    .apiKeyId(created.apiKeyId())
                    .version(created.version())
                    .occurredAt(created.occurredAt())
                    .userId(created.userId())
                    .accessId(created.accessId())
                    .accessSecretHash(created.accessSecretHash())
                    .permissions(toWirePermissions(created.permissions()))
                    .createdAt(created.createdAt())
                    .expiresAt(created.expiresAt())
                    .build();
            case ApiKeyUpdated updated -> UpdateApiKeyProjectionCommand.builder()
                    .apiKeyName(updated.apiKeyName())
                    .apiKeyId(updated.apiKeyId())
                    .version(updated.version())
                    .occurredAt(updated.occurredAt())
                    .userId(updated.userId())
                    .accessId(updated.accessId())
                    .permissions(toWirePermissions(updated.permissions()))
                    .createdAt(updated.createdAt())
                    .expiresAt(updated.expiresAt())
                    .build();
            case ApiKeyDeleted deleted -> DeleteApiKeyProjectionCommand.builder()
                    .apiKeyName(deleted.apiKeyName())
                    .apiKeyId(deleted.apiKeyId())
                    .version(deleted.version())
                    .occurredAt(deleted.occurredAt())
                    .userId(deleted.userId())
                    .accessId(deleted.accessId())
                    .permissions(toWirePermissions(deleted.permissions()))
                    .createdAt(deleted.createdAt())
                    .expiresAt(deleted.expiresAt())
                    .build();
        };
    }

    /**
     * Scope name to level name, ordered by scope declaration. {@link Permissions} never holds {@code NONE}
     * entries, so the map only contains granted levels; an absent scope means no access.
     */
    public static Map<String, String> toWirePermissions(Permissions permissions) {
        Map<String, String> wire = new LinkedHashMap<>();
        permissions.asMap().forEach((scope, level) -> wire.put(scope.name(), level.name()));
        return Collections.unmodifiableMap(wire);
    }
}
