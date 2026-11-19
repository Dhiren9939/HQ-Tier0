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
 * @param batchSize      rows taken per tick
 * @param pollInterval   pause between the end of one tick and the start of the next
 * @param confirmTimeout one deadline for the whole batch's publish confirms, counted from the first send; when it
 *                       passes the batch is cancelled
 */
@ConfigurationProperties("access.outbox.relay")
public record OutboxRelayProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("apikey.projections") String streamName,
        @DefaultValue("2") int partitions,
        @DefaultValue("100") int batchSize,
        @DefaultValue("PT2S") Duration pollInterval,
        @DefaultValue("PT5S") Duration confirmTimeout) {
}
