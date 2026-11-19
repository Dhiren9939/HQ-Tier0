package me.dhiren9939.backend.access.application.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import me.dhiren9939.backend.access.domain.events.ApiKeyCreated;
import me.dhiren9939.backend.access.domain.events.ApiKeyDeleted;
import me.dhiren9939.backend.access.domain.events.ApiKeyEvent;
import me.dhiren9939.backend.access.domain.events.ApiKeyUpdated;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyAccessLevel;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyScope;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Plain mapping: every field is copied as is (the secret hash unchanged) and the domain's scopes and levels become
 * the published contract enums. How the integration event is written as JSON is the serializer's concern. The
 * exhaustive {@code switch}es stop compiling when a domain event type, scope or level is added.
 */
@Component
public class DefaultApiKeyIntegrationEventMapper implements ApiKeyIntegrationEventMapper {

    @Override
    public ApiKeyIntegrationEvent toIntegrationEvent(ApiKeyEvent event) {
        return switch (event) {
            case ApiKeyCreated created -> ApiKeyCreatedIntegrationEvent.builder()
                    .apiKeyName(created.apiKeyName())
                    .apiKeyId(created.apiKeyId())
                    .version(created.version())
                    .occurredAt(created.occurredAt())
                    .userId(created.userId())
                    .accessId(created.accessId())
                    .accessSecretHash(created.accessSecretHash())
                    .permissions(permissions(created.permissions()))
                    .createdAt(created.createdAt())
                    .expiresAt(created.expiresAt())
                    .build();
            case ApiKeyUpdated updated -> ApiKeyUpdatedIntegrationEvent.builder()
                    .apiKeyName(updated.apiKeyName())
                    .apiKeyId(updated.apiKeyId())
                    .version(updated.version())
                    .occurredAt(updated.occurredAt())
                    .userId(updated.userId())
                    .accessId(updated.accessId())
                    .accessSecretHash(updated.accessSecretHash())
                    .permissions(permissions(updated.permissions()))
                    .createdAt(updated.createdAt())
                    .expiresAt(updated.expiresAt())
                    .build();
            case ApiKeyDeleted deleted -> ApiKeyDeletedIntegrationEvent.builder()
                    .apiKeyName(deleted.apiKeyName())
                    .apiKeyId(deleted.apiKeyId())
                    .version(deleted.version())
                    .occurredAt(deleted.occurredAt())
                    .userId(deleted.userId())
                    .accessId(deleted.accessId())
                    .accessSecretHash(deleted.accessSecretHash())
                    .permissions(permissions(deleted.permissions()))
                    .createdAt(deleted.createdAt())
                    .expiresAt(deleted.expiresAt())
                    .build();
        };
    }

    private static Map<ApiKeyScope, ApiKeyAccessLevel> permissions(Permissions permissions) {
        Map<ApiKeyScope, ApiKeyAccessLevel> granted = new EnumMap<>(ApiKeyScope.class);
        permissions.asMap().forEach((scope, level) -> granted.put(scope(scope), level(level)));
        return Collections.unmodifiableMap(granted);
    }

    private static ApiKeyScope scope(Scope scope) {
        return switch (scope) {
            case INGESTION -> ApiKeyScope.INGESTION;
            case TENANT -> ApiKeyScope.TENANT;
            case CHANNEL -> ApiKeyScope.CHANNEL;
        };
    }

    /** {@link Permissions} never holds NONE, so reaching it here is a bug. */
    private static ApiKeyAccessLevel level(AccessLevel level) {
        return switch (level) {
            case READ -> ApiKeyAccessLevel.READ;
            case WRITE -> ApiKeyAccessLevel.WRITE;
            case READ_WRITE -> ApiKeyAccessLevel.READ_WRITE;
            case NONE -> throw new IllegalArgumentException("NONE is never published; leave the scope out");
        };
    }
}
