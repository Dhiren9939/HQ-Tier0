package me.dhiren9939.backend.access.infrastructure.config;

import com.rabbitmq.stream.Environment;
import me.dhiren9939.backend.access.infrastructure.messaging.OutboxRelay;
import me.dhiren9939.backend.access.infrastructure.messaging.OutboxRelayHealth;
import me.dhiren9939.backend.access.infrastructure.messaging.OutboxRelayProperties;
import me.dhiren9939.backend.access.infrastructure.messaging.ProjectionMessages;
import me.dhiren9939.backend.access.infrastructure.messaging.ProjectionStream;
import me.dhiren9939.backend.access.infrastructure.messaging.RabbitProjectionStream;
import me.dhiren9939.backend.access.infrastructure.persistence.repositories.OutboxStore;
import me.dhiren9939.backend.access.infrastructure.serialization.IntegrationEventSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Wires the outbox relay: a super stream producer routed by {@code apiKeyId}, the scheduled relay that feeds it and
 * its health indicator. Set {@code access.outbox.relay.enabled=false} to leave the rows in the outbox (tests without
 * a broker do). The stream {@link Environment} is Spring Boot's, built lazily from {@code spring.rabbitmq.stream.*}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(OutboxRelayProperties.class)
@ConditionalOnProperty(prefix = "access.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayConfig {

    @Bean
    RabbitStreamTemplate projectionStreamTemplate(Environment rabbitStreamEnvironment, OutboxRelayProperties properties) {
        RabbitStreamTemplate template = new RabbitStreamTemplate(rabbitStreamEnvironment, properties.streamName());
        template.setSuperStreamRouting(message ->
                String.valueOf(message.getApplicationProperties().get(ProjectionMessages.API_KEY_ID)));
        return template;
    }

    @Bean
    ProjectionStream projectionStream(Environment rabbitStreamEnvironment, RabbitStreamTemplate projectionStreamTemplate,
                                      OutboxRelayProperties properties) {
        return new RabbitProjectionStream(rabbitStreamEnvironment, projectionStreamTemplate,
                properties.streamName(), properties.partitions());
    }

    @Bean
    OutboxRelay outboxRelay(OutboxStore store, ProjectionStream projectionStream, IntegrationEventSerializer serializer,
                            OutboxRelayProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        return new OutboxRelay(store, projectionStream, serializer, properties, clock,
                new TransactionTemplate(transactionManager));
    }

    @Bean
    OutboxRelayHealth outboxRelayHealth(OutboxRelay outboxRelay) {
        return new OutboxRelayHealth(outboxRelay);
    }
}
