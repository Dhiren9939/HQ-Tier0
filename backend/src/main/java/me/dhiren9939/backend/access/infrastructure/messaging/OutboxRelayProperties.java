package me.dhiren9939.backend.access.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings of the outbox relay ({@code access.outbox.relay.*}).
 *
 * @param enabled        whether the relay runs at all; off means rows simply wait in the outbox
 * @param streamName     the super stream the integration events are published to
 * @param partitions     partitions of the super stream; a key always hashes to the same one
 * @param batchSize      rows taken per cycle
 * @param pollInterval   pause between cycles
 * @param confirmTimeout how long a cycle waits for the broker's publish confirms before dropping the batch
 * @param maxAttempts    refused publishes of one row before it is dead-lettered
 * @param backoffBase    first pause after a transient failure
 * @param backoffMax     longest pause after repeated transient failures
 */
@ConfigurationProperties("access.outbox.relay")
public record OutboxRelayProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("apikey.projections") String streamName,
        @DefaultValue("2") int partitions,
        @DefaultValue("100") int batchSize,
        @DefaultValue("PT1S") Duration pollInterval,
        @DefaultValue("PT10S") Duration confirmTimeout,
        @DefaultValue("10") int maxAttempts,
        @DefaultValue("PT1S") Duration backoffBase,
        @DefaultValue("PT60S") Duration backoffMax) {
}
