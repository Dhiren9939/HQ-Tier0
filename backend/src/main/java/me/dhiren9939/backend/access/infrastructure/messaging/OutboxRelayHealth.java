package me.dhiren9939.backend.access.infrastructure.messaging;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** UP while the relay publishes; DOWN, naming the stuck event and why, once it has halted. */
public class OutboxRelayHealth implements HealthIndicator {
    private final OutboxRelay relay;

    public OutboxRelayHealth(OutboxRelay relay) {
        this.relay = relay;
    }

    @Override
    public Health health() {
        return relay.halt()
                .map(halt -> Health.down()
                        .withDetail("state", "halted until restart")
                        .withDetail("eventId", halt.eventId().toString())
                        .withDetail("eventType", halt.eventType())
                        .withDetail("apiKeyId", halt.apiKeyId().toString())
                        .withDetail("version", halt.version())
                        .withDetail("reason", halt.reason())
                        .withDetail("batch", halt.batch())
                        .build())
                .orElseGet(() -> Health.up().build());
    }
}
