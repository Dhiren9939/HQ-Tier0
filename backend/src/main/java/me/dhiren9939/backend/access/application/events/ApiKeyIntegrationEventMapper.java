package me.dhiren9939.backend.access.application.events;

import me.dhiren9939.backend.access.domain.events.ApiKeyEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;

/**
 * Turns the Access domain's events into the integration events written to the outbox and published to the data
 * plane. This is the only place where the domain model and the published contract meet.
 *
 * <p>It is a bean, not a static utility, so how the contents are encoded or protected before they reach the outbox
 * (for example encrypting the secret hash) can change by providing another implementation, or one that decorates
 * {@link DefaultApiKeyIntegrationEventMapper}, without touching the domain, the persistence adapter or the outbox.</p>
 */
public interface ApiKeyIntegrationEventMapper {

    ApiKeyIntegrationEvent toIntegrationEvent(ApiKeyEvent event);
}
