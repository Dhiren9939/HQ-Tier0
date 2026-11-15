package me.dhiren9939.backend.access.infrastructure.persistence.repositories;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.IssuedCredential;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
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

/** The repository implementation writes the aggregate's projection commands into the outbox itself, right after the key change. */
@ExtendWith(MockitoExtension.class)
class ApiKeysAdapterTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    @Mock private JpaApiKeyRepository repository;
    @Mock private EventOutboxRepository outboxRepository;

    private ApiKeysAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ApiKeysAdapter(repository, outboxRepository);
    }

    private static ApiKey newKey() {
        return ApiKey.issue(UUID.randomUUID(), "k", new IssuedCredential("V1_acc", "secret", "hash"),
                Permissions.none(), NOW, null);
    }

    @Test
    void savingANewKeyWritesItsCreatedEventToTheOutbox() {
        ApiKey key = newKey();
        when(repository.save(key)).thenReturn(key);

        adapter.save(key);

        ArgumentCaptor<EventOutboxEntity> row = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(row.capture());
        assertThat(row.getValue().getEventType()).isEqualTo("apiKey.projection.create");
        assertThat(row.getValue().getApiKeyId()).isEqualTo(key.getApiKeyId());
        assertThat(key.pullDomainEvents()).as("events are drained once written").isEmpty();
    }

    @Test
    void savingAKeyWithoutNewEventsWritesNothing() {
        ApiKey key = newKey();
        key.pullDomainEvents();
        when(repository.save(key)).thenReturn(key);

        adapter.save(key);

        verifyNoInteractions(outboxRepository);
    }

    @Test
    void aFailingOutboxWriteSurfacesSoTheTransactionRollsBack() {
        ApiKey key = newKey();
        when(repository.save(key)).thenReturn(key);
        when(outboxRepository.save(any())).thenThrow(new IllegalStateException("outbox down"));

        assertThatThrownBy(() -> adapter.save(key)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingAKeyWritesItsEventsToTheOutbox() {
        ApiKey key = newKey();

        adapter.delete(key);

        verify(repository).delete(key);
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }
}
