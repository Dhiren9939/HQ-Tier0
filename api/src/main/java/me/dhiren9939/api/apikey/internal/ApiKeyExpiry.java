package me.dhiren9939.api.apikey.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Calculates API key expiration timestamps for supported expiry durations.
 */
@Slf4j
@Service
class ApiKeyExpiry {
    /** Supported API key lifetime choices. */
    enum Duration {
        ONE_DAY,
        ONE_MONTH,
        NEVER
    }

    Instant getExpiry(Instant now, Duration duration) {
        return switch (duration) {
            case ONE_DAY -> now.plus(1, ChronoUnit.DAYS);
            case ONE_MONTH -> now.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
            case NEVER -> null;
        };
    }
}
