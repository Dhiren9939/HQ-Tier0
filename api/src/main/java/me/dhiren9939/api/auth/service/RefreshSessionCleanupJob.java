package me.dhiren9939.api.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.auth.jwt.RefreshTokenProperties;
import me.dhiren9939.api.auth.repo.RefreshSessionRepo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Without this, refresh_sessions only ever grows: every rotation leaves its old row behind
 * (revoked, not deleted - rotate() needs it to still exist momentarily to detect reuse), every
 * logout leaves a revoked row, every session that's simply never refreshed again sits there
 * expired-but-present forever. None of those rows are usable once expired/revoked - rotate()
 * already rejects them on their own merits - so deleting them changes nothing about behavior,
 * only about table size.
 *
 * Revoked rows are kept for a retention window first (not deleted immediately) so a reuse
 * incident can still be investigated - "was this session part of a chain that got revoked, and
 * when" - shortly after the fact.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshSessionCleanupJob {

    private final RefreshSessionRepo refreshSessionRepo;
    private final RefreshTokenProperties properties;

    @Scheduled(cron = "0 0 3 * * *") // once a day, low-traffic hour; exact time doesn't matter
    @Transactional
    public void purgeDeadSessions() {
        Instant now = Instant.now();
        Instant revokedBefore = now.minus(Duration.ofDays(properties.getRevokedRetentionDays()));

        int deleted = refreshSessionRepo.purgeDead(now, revokedBefore);
        if (deleted > 0) {
            log.info("Purged {} dead refresh sessions (expired, or revoked before {})", deleted, revokedBefore);
        }
    }
}
