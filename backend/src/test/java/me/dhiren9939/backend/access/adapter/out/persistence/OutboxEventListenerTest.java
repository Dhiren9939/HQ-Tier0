package me.dhiren9939.backend.access.adapter.out.persistence;

import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.events.ApiKeyCreated;
import me.dhiren9939.backend.shared.access.events.ApiKeyDeleted;
import me.dhiren9939.backend.shared.access.events.ApiKeyEvent;
import me.dhiren9939.backend.shared.access.events.ApiKeyUpdated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/** Pins down how published events map to outbox rows, and when the listener runs. */
@ExtendWith(MockitoExtension.class)
class OutboxEventListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:00:00Z");
    private static final UUID API_KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Permissions PERMISSIONS = Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE).with(Scope.TENANT, AccessLevel.READ);

    @Mock private EventOutboxRepository outboxRepository;
    @Captor private ArgumentCaptor<EventOutboxEntity> rowCaptor;

    private OutboxEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OutboxEventListener(outboxRepository);
    }

    private EventOutboxEntity savedRow() {
        verify(outboxRepository).save(rowCaptor.capture());
        return rowCaptor.getValue();
    }

    @Test
    void runsBeforeCommitSoTheRowIsPartOfTheSameTransaction() throws NoSuchMethodException {
        TransactionalEventListener annotation = OutboxEventListener.class
                .getDeclaredMethod("handle", ApiKeyEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
    }

    @Test
    void createdEventBecomesARowWithTheSecretHash() {
        listener.handle(ApiKeyCreated.builder()
                .apiKeyName("test-key").apiKeyId(API_KEY_ID).version(1).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc").accessSecretHash("hashed_secret")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build());

        EventOutboxEntity row = savedRow();
        assertThat(row.getEventType()).isEqualTo("apiKey.created");
        assertThat(row.getApiKeyId()).isEqualTo(API_KEY_ID);
        assertThat(row.getVersion()).isEqualTo(1);
        assertThat(row.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(row.getEmittedAt()).isNull();
        assertThat(row.getUserId()).isEqualTo(USER_ID);
        assertThat(row.getAccessId()).isEqualTo("V1_acc");
        assertThat(row.getAccessSecretHash()).isEqualTo("hashed_secret");
        assertThat(row.getApiKeyName()).isEqualTo("test-key");
        assertThat(row.getPermissions()).isEqualTo("{\"INGESTION\":\"WRITE\",\"TENANT\":\"READ\"}");
        assertThat(row.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void updatedEventBecomesARowWithoutTheSecretHash() {
        listener.handle(ApiKeyUpdated.builder()
                .apiKeyName("renamed").apiKeyId(API_KEY_ID).version(2).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build());

        EventOutboxEntity row = savedRow();
        assertThat(row.getEventType()).isEqualTo("apiKey.updated");
        assertThat(row.getVersion()).isEqualTo(2);
        assertThat(row.getApiKeyName()).isEqualTo("renamed");
        assertThat(row.getAccessSecretHash()).isNull();
    }

    @Test
    void deletedEventBecomesARowWithoutTheSecretHash() {
        listener.handle(ApiKeyDeleted.builder()
                .apiKeyName("test-key").apiKeyId(API_KEY_ID).version(2).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc")
                .permissions(PERMISSIONS).createdAt(CREATED_AT).build());

        EventOutboxEntity row = savedRow();
        assertThat(row.getEventType()).isEqualTo("apiKey.deleted");
        assertThat(row.getVersion()).isEqualTo(2);
        assertThat(row.getAccessSecretHash()).isNull();
    }

    @Test
    void aFailingOutboxWriteSurfacesSoTheTransactionRollsBack() {
        org.mockito.Mockito.when(outboxRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("outbox down"));

        assertThatThrownBy(() -> listener.handle(ApiKeyDeleted.builder()
                .apiKeyName("k").apiKeyId(API_KEY_ID).version(2).occurredAt(OCCURRED_AT)
                .userId(USER_ID).accessId("V1_acc").permissions(PERMISSIONS).createdAt(CREATED_AT).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void permissionsAreStoredAsAJsonObjectKeyedByScopeWithAbsentMeaningNone() {
        assertThat(EventOutboxEntity.permissionsJson(Permissions.none())).isEqualTo("{}");
        assertThat(EventOutboxEntity.permissionsJson(
                Permissions.none().with(Scope.CHANNEL, AccessLevel.READ_WRITE).with(Scope.INGESTION, AccessLevel.WRITE)))
                .isEqualTo("{\"INGESTION\":\"WRITE\",\"CHANNEL\":\"READ_WRITE\"}");
    }
}
