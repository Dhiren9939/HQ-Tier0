package me.dhiren9939.backend.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can pin to a moment or move forward; follows the system clock until pinned. */
public class MutableClock extends Clock {
    private volatile Instant pinned;

    /** Pins the clock to {@code instant} until {@link #reset()} is called. */
    public void pinTo(Instant instant) {
        this.pinned = instant;
    }

    /** Back to the system clock. */
    public void reset() {
        this.pinned = null;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        Instant current = pinned;
        return current != null ? current : Instant.now();
    }
}
