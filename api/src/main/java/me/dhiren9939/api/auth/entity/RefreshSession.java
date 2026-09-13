package me.dhiren9939.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions", indexes = @Index(name = "idx_refresh_session_user_id", columnList = "userId"))
@Getter
@Setter
@NoArgsConstructor
public class RefreshSession {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID sessionId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String refreshTokenHash;

    private String deviceInfo;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    // Set when this token is presented after it's already been rotated/logged-out.
    // A non-null value on the token being checked means reuse - revoke the whole family.
    private Instant revokedAt;

    // Points at the session row created when this one was rotated, so a reuse can be
    // traced forward through the chain, not just flagged at the row it was caught on.
    private UUID replacedBy;

    // Updated on every successful refresh; lets the "active devices" list show
    // last-seen time instead of just when the session was first created.
    private Instant lastUsedAt;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
