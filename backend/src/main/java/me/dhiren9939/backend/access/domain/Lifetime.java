package me.dhiren9939.backend.access.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Lifetime choices an owner can pick for an API key. */
public enum Lifetime {
    ONE_DAY,
    ONE_MONTH,
    NEVER;

    /** Resolves this lifetime to an expiry instant counted from {@code now}; {@code null} means never. */
    public Instant expiryFrom(Instant now) {
        return switch (this) {
            case ONE_DAY -> now.plus(1, ChronoUnit.DAYS);
            case ONE_MONTH -> now.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
            case NEVER -> null;
        };
    }
}
