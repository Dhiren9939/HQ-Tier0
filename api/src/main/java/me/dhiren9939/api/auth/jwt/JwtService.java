package me.dhiren9939.api.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Signs and verifies the short-lived access token carried in the access-token cookie.
 * This never touches refresh tokens/sessions - that's DB-backed, not JWT-backed.
 */
@Service
public class JwtService {

    private static final String EMAIL_CLAIM = "email";

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
        this.accessTokenTtl = Duration.ofMinutes(properties.getAccessTokenTtlMinutes());
    }

    public String generateAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(EMAIL_CLAIM, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies signature and expiry. Returns empty on anything wrong with the token
     * (expired, tampered, malformed) - callers treat that as "not authenticated",
     * never distinguishing the reason back to the client.
     */
    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get(EMAIL_CLAIM, String.class);
            return Optional.of(new AccessTokenClaims(userId, email));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }
}
