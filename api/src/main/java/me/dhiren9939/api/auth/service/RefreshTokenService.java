package me.dhiren9939.api.auth.service;

import me.dhiren9939.api.auth.entity.RefreshSession;
import me.dhiren9939.api.auth.jwt.RefreshTokenProperties;
import me.dhiren9939.api.auth.repo.RefreshSessionRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the DB-backed side of refresh tokens: creating a device's {@link RefreshSession} row,
 * producing the opaque cookie value for it, and rotating it on every refresh. Deliberately
 * separate from JwtService - a refresh token is never a JWT, it's a random secret whose hash
 * lives in the DB so it can be looked up, revoked and rotated.
 */
@Service
public class RefreshTokenService {

    private static final int SECRET_BYTES = 32;

    private final RefreshSessionRepo refreshSessionRepo;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshSessionRepo refreshSessionRepo, RefreshTokenProperties properties) {
        this.refreshSessionRepo = refreshSessionRepo;
        this.refreshTokenTtl = Duration.ofDays(properties.getTtlDays());
    }

    /** Result of issuing a new session: the raw cookie value (never stored) and its expiry. */
    public record IssuedRefreshToken(String cookieValue, Instant expiresAt) {
    }

    /** Outcome of presenting a refresh token. Exhaustive - a caller has to handle all three. */
    public sealed interface RotateResult permits RotateResult.Rotated, RotateResult.Reused, RotateResult.Invalid {
        record Rotated(IssuedRefreshToken token, UUID userId) implements RotateResult {
        }

        /** The presented token had already been rotated/revoked - a replay. Every session for the user is now dead. */
        record Reused(UUID userId) implements RotateResult {
        }

        /** Missing cookie, malformed value, unknown session, wrong secret, or expired. No further detail on purpose. */
        record Invalid() implements RotateResult {
        }
    }

    public IssuedRefreshToken issue(UUID userId, String deviceInfo) {
        String secret = generateSecret();
        RefreshSession session = createSession(userId, deviceInfo, secret);
        return toIssued(session, secret);
    }

    /**
     * Revokes the single session named by this cookie (logout from this device only).
     * Deliberately silent and idempotent on a missing/malformed/already-revoked token - a
     * logout call has nothing further to prove or protect once the cookie is gone either way.
     */
    @Transactional
    public void revoke(String cookieValue) {
        parseCookieValue(cookieValue)
                .flatMap(parsed -> refreshSessionRepo.findById(parsed.sessionId()))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> {
                    session.setRevokedAt(Instant.now());
                    refreshSessionRepo.save(session);
                });
    }

    @Transactional
    public RotateResult rotate(String cookieValue, String deviceInfo) {
        Optional<ParsedToken> parsed = parseCookieValue(cookieValue);
        if (parsed.isEmpty()) {
            return new RotateResult.Invalid();
        }

        Optional<RefreshSession> maybeSession = refreshSessionRepo.findById(parsed.get().sessionId());
        if (maybeSession.isEmpty()) {
            return new RotateResult.Invalid();
        }

        RefreshSession session = maybeSession.get();
        if (!matchesHash(parsed.get().secret(), session.getRefreshTokenHash())) {
            return new RotateResult.Invalid();
        }

        Instant now = Instant.now();

        if (session.getRevokedAt() != null) {
            // This exact token was already rotated away (or logged out) - someone is replaying an
            // old cookie. Could be the legitimate device racing its own rotation, but we can't tell
            // the difference from an attacker who stole a cookie, so treat it as compromise: kill
            // every active session this user has, forcing a fresh login everywhere.
            refreshSessionRepo.revokeAllActiveForUser(session.getUserId(), now);
            return new RotateResult.Reused(session.getUserId());
        }

        if (session.getExpiresAt().isBefore(now)) {
            return new RotateResult.Invalid();
        }

        String newSecret = generateSecret();
        RefreshSession next = createSession(session.getUserId(), deviceInfo, newSecret);

        session.setRevokedAt(now);
        session.setReplacedBy(next.getSessionId());
        refreshSessionRepo.save(session);

        return new RotateResult.Rotated(toIssued(next, newSecret), session.getUserId());
    }

    private RefreshSession createSession(UUID userId, String deviceInfo, String secret) {
        Instant now = Instant.now();
        RefreshSession session = new RefreshSession();
        session.setUserId(userId);
        session.setRefreshTokenHash(hash(secret));
        session.setDeviceInfo(deviceInfo);
        session.setIssuedAt(now);
        session.setExpiresAt(now.plus(refreshTokenTtl));
        session.setLastUsedAt(now);
        return refreshSessionRepo.save(session);
    }

    private IssuedRefreshToken toIssued(RefreshSession session, String secret) {
        // "<sessionId>.<secret>" so a refresh lookup is a PK read, not a table scan over every
        // hash - the secret is only ever compared against the hash of that one row.
        return new IssuedRefreshToken(session.getSessionId() + "." + secret, session.getExpiresAt());
    }

    private record ParsedToken(UUID sessionId, String secret) {
    }

    private Optional<ParsedToken> parseCookieValue(String cookieValue) {
        if (cookieValue == null) {
            return Optional.empty();
        }
        int separator = cookieValue.indexOf('.');
        if (separator <= 0 || separator == cookieValue.length() - 1) {
            return Optional.empty();
        }
        try {
            UUID sessionId = UUID.fromString(cookieValue.substring(0, separator));
            String secret = cookieValue.substring(separator + 1);
            return Optional.of(new ParsedToken(sessionId, secret));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String secret) {
        return HexFormat.of().formatHex(digest(secret));
    }

    /** Constant-time comparison - String.equals()/hex-string equality would leak timing on a byte-by-byte match. */
    private boolean matchesHash(String secret, String expectedHex) {
        return MessageDigest.isEqual(digest(secret), HexFormat.of().parseHex(expectedHex));
    }

    private byte[] digest(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM - this can't actually happen.
            throw new IllegalStateException(e);
        }
    }
}
