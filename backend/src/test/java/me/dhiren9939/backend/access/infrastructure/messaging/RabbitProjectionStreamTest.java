package me.dhiren9939.backend.access.infrastructure.messaging;

import com.rabbitmq.stream.Constants;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.codec.WrapperMessageBuilder;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;
import org.springframework.rabbit.stream.producer.StreamSendException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** How the RabbitMQ adapter shapes the message and turns the template's answers into confirm, refusal or outage. */
class RabbitProjectionStreamTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID API_KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final RabbitStreamTemplate template = mock(RabbitStreamTemplate.class);
    private final RabbitProjectionStream stream = new RabbitProjectionStream(null, template, "apikey.projections", 2);
    private final EventOutboxEntity row = EventOutboxEntity.builder().eventId(EVENT_ID)
            .eventType("apiKey.projection.update").apiKeyId(API_KEY_ID).version(3).payload("{}").build();

    @BeforeEach
    void setUp() {
        when(template.messageBuilder()).thenAnswer(invocation -> new WrapperMessageBuilder());
    }

    private CompletableFuture<Boolean> sendAnswered(CompletableFuture<Boolean> answer) {
        when(template.send(any(Message.class))).thenReturn(answer);
        return stream.send(row, "{\"sent\":true}");
    }

    @Test
    void theMessageCarriesTheIdRoutingPropertiesAndTheBodyAsGiven() {
        sendAnswered(CompletableFuture.completedFuture(true));

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(template).send(sent.capture());
        Message message = sent.getValue();
        assertThat(message.getProperties().getMessageIdAsUuid()).isEqualTo(EVENT_ID);
        assertThat(message.getProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getApplicationProperties())
                .containsEntry("eventType", "apiKey.projection.update")
                .containsEntry("apiKeyId", API_KEY_ID.toString())
                .containsEntry("version", 3L);
        assertThat(new String(message.getBodyAsBinary(), StandardCharsets.UTF_8)).isEqualTo("{\"sent\":true}");
    }

    @Test
    void aConfirmIsTrue() {
        assertThat(sendAnswered(CompletableFuture.completedFuture(true))).isCompletedWithValue(true);
    }

    @Test
    void aBrokerAnswerAboutTheMessageIsARefusal() {
        CompletableFuture<Boolean> result = sendAnswered(CompletableFuture.failedFuture(
                new StreamSendException("refused", Constants.RESPONSE_CODE_ACCESS_REFUSED)));

        assertThat(result).isCompletedExceptionally();
        assertThat(result.exceptionNow()).isInstanceOf(ProjectionRefusedException.class)
                .hasMessage("the broker refused the message: ACCESS_REFUSED (16)");
    }

    @Test
    void aClientOrConnectionCodeIsAnOutage() {
        for (short code : new short[]{Constants.CODE_PRODUCER_CLOSED, Constants.CODE_PUBLISH_CONFIRM_TIMEOUT,
                Constants.CODE_PRODUCER_NOT_AVAILABLE, Constants.CODE_MESSAGE_ENQUEUEING_FAILED,
                Constants.RESPONSE_CODE_STREAM_NOT_AVAILABLE, Constants.RESPONSE_CODE_STREAM_DOES_NOT_EXIST,
                Constants.RESPONSE_CODE_PUBLISHER_DOES_NOT_EXIST, Constants.RESPONSE_CODE_INTERNAL_ERROR}) {
            CompletableFuture<Boolean> result = sendAnswered(CompletableFuture.failedFuture(
                    new StreamSendException("down", code)));

            assertThat(result.exceptionNow()).as("code %s", code).isInstanceOf(ProjectionUnavailableException.class);
        }
    }

    @Test
    void anyOtherFailureIsAnOutage() {
        CompletableFuture<Boolean> result = sendAnswered(CompletableFuture.failedFuture(new IllegalStateException("closed")));

        assertThat(result.exceptionNow()).isInstanceOf(ProjectionUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void theReconnectRaceAndBrokerInternalErrorsAreOutagesNotHalts() {
        assertThat(sendAnswered(CompletableFuture.failedFuture(new StreamSendException("reconnecting",
                Constants.RESPONSE_CODE_PUBLISHER_DOES_NOT_EXIST))).exceptionNow())
                .isInstanceOf(ProjectionUnavailableException.class)
                .hasMessage("the broker could not take the message right now: PUBLISHER_DOES_NOT_EXIST (18)");
        assertThat(sendAnswered(CompletableFuture.failedFuture(new StreamSendException("oops",
                Constants.RESPONSE_CODE_INTERNAL_ERROR))).exceptionNow())
                .isInstanceOf(ProjectionUnavailableException.class)
                .hasMessage("the broker could not take the message right now: INTERNAL_ERROR (15)");
    }

    @Test
    void codesAreNamed() {
        assertThat(RabbitProjectionStream.describe(Constants.CODE_NO_ROUTE_FOUND)).isEqualTo("NO_ROUTE_FOUND (10005)");
        assertThat(RabbitProjectionStream.describe(Constants.RESPONSE_CODE_ACCESS_REFUSED)).isEqualTo("ACCESS_REFUSED (16)");
        assertThat(RabbitProjectionStream.describe((short) 999)).isEqualTo("code 999");
    }
}
