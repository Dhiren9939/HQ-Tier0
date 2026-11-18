package me.dhiren9939.backend.access.application.events;

import me.dhiren9939.backend.access.domain.events.ApiKeyCreated;
import me.dhiren9939.backend.access.domain.events.ApiKeyDeleted;
import me.dhiren9939.backend.access.domain.events.ApiKeyEvent;
import me.dhiren9939.backend.access.domain.events.ApiKeyUpdated;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyDeletedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyUpdatedIntegrationEvent;
import org.springframework.stereotype.Component;

/**
 * Plain mapping: every field is copied as is (the secret hash unchanged, permissions as a scope-name to
 * level-name map). The exhaustive {@code switch} stops compiling when a domain event type is added.
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
                    .permissions(WirePermissions.of(created.permissions()))
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
                    .permissions(WirePermissions.of(updated.permissions()))
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
                    .permissions(WirePermissions.of(deleted.permissions()))
                    .createdAt(deleted.createdAt())
                    .expiresAt(deleted.expiresAt())
                    .build();
        };
    }
}
