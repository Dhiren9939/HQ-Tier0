package me.dhiren9939.api.apikeys.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * How long a newly (re)computed API key secret should live from the moment it's set.
 */
@Service
public class ApiKeyExpiry {

    public enum Duration {
        ONE_DAY,
        ONE_MONTH,
        NEVER
    }

    public Instant getExpiry(Instant now, String duration) {
        // Instant only supports precisions up to DAYS - ChronoUnit.MONTHS throws
        // UnsupportedTemporalTypeException on it, so calendar-based math goes through a zone.
        return switch (Duration.valueOf(duration)) {
            case ONE_DAY -> now.plus(1, ChronoUnit.DAYS);
            case ONE_MONTH -> now.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
            case NEVER -> null;
        };
    }
}
