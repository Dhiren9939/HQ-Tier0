package me.dhiren9939.backend.access.infrastructure.persistence.repositories;

import me.dhiren9939.backend.access.application.events.ApiKeyIntegrationEventMapper;
import me.dhiren9939.backend.access.application.events.DefaultApiKeyIntegrationEventMapper;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.domain.aggregates.apikey.IssuedCredential;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyCreatedIntegrationEvent;
import me.dhiren9939.backend.common.access.events.outbound.ApiKeyIntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The repository implementation maps the aggregate's events and appends them to the outbox right after the key change. */
@ExtendWith(MockitoExtension.class)
class ApiKeysAdapterTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    @Mock private JpaApiKeyRepository repository;
    @Mock private OutboxStore outbox;

    private ApiKeysAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ApiKeysAdapter(repository, outbox, new DefaultApiKeyIntegrationEventMapper());
    }

    private static ApiKey newKey() {
        return ApiKey.issue(UUID.randomUUID(), "k", new IssuedCredential("V1_acc", "secret", "hash"),
                Permissions.none(), Lifetime.NEVER, NOW);
    }

    @Test
    void savingANewKeyAppendsItsCreatedEventToTheOutbox() {
        ApiKey key = newKey();
        when(repository.save(key)).thenReturn(key);

        adapter.save(key);

        ArgumentCaptor<ApiKeyIntegrationEvent> event = ArgumentCaptor.forClass(ApiKeyIntegrationEvent.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("apiKey.projection.create");
        assertThat(event.getValue().apiKeyId()).isEqualTo(key.getApiKeyId());
        assertThat(key.pullDomainEvents()).as("events are drained once written").isEmpty();
    }

    @Test
    void savingAKeyWithoutNewEventsWritesNothing() {
        ApiKey key = newKey();
        key.pullDomainEvents();
        when(repository.save(key)).thenReturn(key);

        adapter.save(key);

        verifyNoInteractions(outbox);
    }

    @Test
    void aFailingOutboxAppendSurfacesSoTheTransactionRollsBack() {
        ApiKey key = newKey();
        when(repository.save(key)).thenReturn(key);
        doThrow(new IllegalStateException("outbox down")).when(outbox).append(any());

        assertThatThrownBy(() -> adapter.save(key)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingAKeyAppendsItsEventsToTheOutbox() {
        ApiKey key = newKey();

        adapter.delete(key);

        verify(repository).delete(key);
        verify(outbox).append(any(ApiKeyIntegrationEvent.class));
    }

    @Test
    void theOutboxReceivesWhatTheInjectedMapperProduces() {
        // Swapping the mapper is how the event contents get encoded or encrypted before they reach the outbox.
        ApiKeyIntegrationEventMapper encrypting = event -> {
            ApiKeyCreatedIntegrationEvent plain =
                    (ApiKeyCreatedIntegrationEvent) new DefaultApiKeyIntegrationEventMapper().toIntegrationEvent(event);
            return plain.toBuilder().accessSecretHash("enc(" + plain.accessSecretHash() + ")").build();
        };
        ApiKeysAdapter withEncryption = new ApiKeysAdapter(repository, outbox, encrypting);
        ApiKey key = newKey();
        when(repository.save(key)).thenReturn(key);

        withEncryption.save(key);

        ArgumentCaptor<ApiKeyIntegrationEvent> event = ArgumentCaptor.forClass(ApiKeyIntegrationEvent.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().accessSecretHash()).isEqualTo("enc(hash)");
    }
}
