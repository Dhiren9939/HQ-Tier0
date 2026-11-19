package me.dhiren9939.backend.access.infrastructure.messaging;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** UP while the relay publishes; DOWN, naming the stuck event and the error, once it has halted. */
public class OutboxRelayHealth implements HealthIndicator {
    private final OutboxRelay relay;

    public OutboxRelayHealth(OutboxRelay relay) {
        this.relay = relay;
    }

    @Override
    public Health health() {
        return relay.halt()
                .map(halt -> Health.down()
                        .withDetail("eventId", halt.eventId().toString())
                        .withDetail("eventType", halt.eventType())
                        .withDetail("error", halt.error())
                        .build())
                .orElseGet(() -> Health.up().build());
    }
}
