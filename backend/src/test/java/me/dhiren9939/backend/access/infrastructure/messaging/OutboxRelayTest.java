package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Constants;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageBuilder;
import com.rabbitmq.stream.StreamException;
import com.rabbitmq.stream.codec.WrapperMessageBuilder;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import me.dhiren9939.backend.access.infrastructure.persistence.repositories.OutboxStore;
import me.dhiren9939.backend.integration.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.rabbit.stream.producer.StreamSendException;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The relay's decisions: what gets stamped, retried, backed off or dead-lettered. */
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    private final MutableClock clock = new MutableClock();
    private final FakeStream stream = new FakeStream();
    private final OutboxStore store = mock(OutboxStore.class);
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        clock.pinTo(NOW);
        // markEmitted and recordFailure only change the managed rows, so their real code runs on the mock.
        doCallRealMethod().when(store).markEmitted(any(), any());
        when(store.recordFailure(any(), anyString())).thenCallRealMethod();
        relay = relay(3);
    }

    private OutboxRelay relay(int maxAttempts) {
        OutboxRelayProperties properties = new OutboxRelayProperties(true, "apikey.projections", 2, 100,
                Duration.ofSeconds(1), Duration.ofMillis(200), maxAttempts, Duration.ofSeconds(1), Duration.ofSeconds(4));
        return new OutboxRelay(store, stream, new IntegrationEventMessages(JsonMapper.builder().build()), properties,
                clock, TransactionOperations.withoutTransaction());
    }

    private static EventOutboxEntity row(UUID apiKeyId, long version) {
        return EventOutboxEntity.builder()
                .eventId(UUID.randomUUID())
                .eventType("apiKey.projection.update")
                .apiKeyId(apiKeyId)
                .version(version)
                .occurredAt(NOW.minusSeconds(10 - version))
                .userId(UUID.randomUUID())
                .accessId("V1_acc")
                .accessSecretHash("hash")
                .apiKeyName("k")
                .permissions("{}")
                .createdAt(NOW.minusSeconds(60))
                .build();
    }

    private void pending(EventOutboxEntity... rows) {
        when(store.lockPending(anyInt())).thenReturn(List.of(rows));
    }

    @Test
    void anEmptyOutboxDoesNothing() {
        pending();

        relay.poll();

        assertThat(stream.sent).isEmpty();
        assertThat(stream.declared).isFalse();
    }

    @Test
    void confirmedRowsAreStampedAndSentInOutboxOrder() {
        UUID key = UUID.randomUUID();
        EventOutboxEntity first = row(key, 1);
        EventOutboxEntity second = row(key, 2);
        pending(first, second);

        relay.poll();

        assertThat(stream.declared).isTrue();
        assertThat(stream.sent).extracting(message -> message.getApplicationProperties().get("version"))
                .containsExactly(1L, 2L);
        assertThat(first.getEmittedAt()).isEqualTo(NOW);
        assertThat(second.getEmittedAt()).isEqualTo(NOW);
    }

    @Test
    void aConfirmThatNeverArrivesDropsTheBatch() {
        EventOutboxEntity confirmed = row(UUID.randomUUID(), 1);
        EventOutboxEntity unconfirmed = row(UUID.randomUUID(), 1);
        pending(confirmed, unconfirmed);
        stream.confirm = message -> message.getProperties().getMessageIdAsUuid().equals(unconfirmed.getEventId())
                ? new CompletableFuture<>()
                : CompletableFuture.completedFuture(true);

        relay.poll();

        assertThat(confirmed.getEmittedAt()).as("nothing is stamped without every confirm").isNull();
        assertThat(unconfirmed.getEmittedAt()).isNull();
        assertThat(unconfirmed.getAttempts()).as("transient failures do not count").isZero();
        verify(store, never()).deadLetter(any(), any(), any());
        assertThat(relay.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void aTransientConfirmFailureDropsTheBatch() {
        EventOutboxEntity row = row(UUID.randomUUID(), 1);
        pending(row);
        stream.confirm = message -> CompletableFuture.failedFuture(
                new StreamSendException("Producer Not Available", Constants.CODE_PRODUCER_NOT_AVAILABLE));

        relay.poll();

        assertThat(row.getEmittedAt()).isNull();
        assertThat(row.getAttempts()).isZero();
        assertThat(relay.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void anUnreachableBrokerDropsTheBatchAndBacksOffExponentiallyUpToTheCap() {
        EventOutboxEntity row = row(UUID.randomUUID(), 1);
        pending(row);
        stream.declareFailure = new StreamException("connection refused");

        relay.poll();
        assertThat(relay.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));

        clock.pinTo(NOW.plusMillis(500));
        relay.poll();
        verify(store, times(1)).lockPending(anyInt()); // still inside the backoff window: the cycle is skipped

        Instant t = NOW.plusSeconds(1);
        for (long expected : new long[]{2, 4, 4}) {
            clock.pinTo(t);
            relay.poll();
            assertThat(relay.nextAttemptAt()).isEqualTo(t.plusSeconds(expected));
            t = relay.nextAttemptAt();
        }
        assertThat(row.getEmittedAt()).isNull();
        assertThat(row.getAttempts()).isZero();
    }

    @Test
    void theBackoffResetsAfterASuccessfulCycle() {
        EventOutboxEntity row = row(UUID.randomUUID(), 1);
        pending(row);
        stream.declareFailure = new StreamException("connection refused");
        relay.poll();

        stream.declareFailure = null;
        clock.pinTo(NOW.plusSeconds(1));
        relay.poll();
        assertThat(row.getEmittedAt()).isEqualTo(NOW.plusSeconds(1));

        stream.declareFailure = new StreamException("connection refused");
        clock.pinTo(NOW.plusSeconds(2));
        relay.poll();
        assertThat(relay.nextAttemptAt()).as("back to the base delay").isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void aRefusedMessageCountsAgainstItsRowWhileTheRestOfTheBatchIsStamped() {
        EventOutboxEntity refused = row(UUID.randomUUID(), 1);
        EventOutboxEntity fine = row(UUID.randomUUID(), 1);
        pending(refused, fine);
        stream.confirm = message -> message.getProperties().getMessageIdAsUuid().equals(refused.getEventId())
                ? CompletableFuture.failedFuture(new StreamSendException("No route", Constants.CODE_NO_ROUTE_FOUND))
                : CompletableFuture.completedFuture(true);

        relay.poll();

        assertThat(fine.getEmittedAt()).isEqualTo(NOW);
        assertThat(refused.getEmittedAt()).isNull();
        assertThat(refused.getAttempts()).isEqualTo(1);
        assertThat(refused.getLastError()).contains("No route");
        verify(store, never()).deadLetter(any(), any(), any());
        assertThat(relay.nextAttemptAt()).as("a refusal is not an outage").isEqualTo(Instant.MIN);
    }

    @Test
    void aNegativeConfirmIsARefusal() {
        EventOutboxEntity row = row(UUID.randomUUID(), 1);
        pending(row);
        stream.confirm = message -> CompletableFuture.completedFuture(false);

        relay.poll();

        assertThat(row.getEmittedAt()).isNull();
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void aRowIsDeadLetteredOnceItsRefusalsReachMaxAttempts() {
        EventOutboxEntity row = row(UUID.randomUUID(), 1);
        row.setAttempts(2);
        pending(row);
        stream.confirm = message -> CompletableFuture.completedFuture(false);

        relay.poll();

        assertThat(row.getAttempts()).isEqualTo(3);
        verify(store).deadLetter(eq(row), anyString(), eq(NOW));
    }

    @Test
    void anUnpublishableRowIsDeadLetteredAtOnceAndTheOthersStillGo() {
        EventOutboxEntity broken = row(UUID.randomUUID(), 1);
        broken.setPermissions("not json");
        EventOutboxEntity fine = row(UUID.randomUUID(), 1);
        pending(broken, fine);

        relay.poll();

        verify(store).deadLetter(eq(broken), anyString(), eq(NOW));
        assertThat(stream.sent).hasSize(1);
        assertThat(fine.getEmittedAt()).isEqualTo(NOW);
    }

    /** A stream that records what it was sent and answers with whatever confirm the test chooses. */
    static class FakeStream implements ProjectionStream {
        final List<Message> sent = new ArrayList<>();
        Function<Message, CompletableFuture<Boolean>> confirm = message -> CompletableFuture.completedFuture(true);
        RuntimeException declareFailure;
        boolean declared;

        @Override
        public void declare() {
            if (declareFailure != null)
                throw declareFailure;
            declared = true;
        }

        @Override
        public MessageBuilder messageBuilder() {
            return new WrapperMessageBuilder();
        }

        @Override
        public CompletableFuture<Boolean> send(Message message) {
            sent.add(message);
            return confirm.apply(message);
        }
    }
}
