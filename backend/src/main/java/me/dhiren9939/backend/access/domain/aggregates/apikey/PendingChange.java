package me.dhiren9939.backend.access.domain.aggregates.apikey;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * A requested change to an API key that waits for the data plane before it takes effect.
 * Owned by exactly one {@link ApiKey}; at most one exists per key.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pending_changes", schema = "access")
public class PendingChange {

    @Id
    @Column(name = "change_id")
    private UUID changeId;

    @Column(nullable = false)
    private long revision;

    @Column(nullable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChangeKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChangeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private FailureReason failureReason;

    // The attributes the key will have once this change is confirmed.
    @Column(nullable = false, length = 256)
    private String apiKeyName;

    /**
     * The permissions the key will have once confirmed, as scope-to-level rows. Persistence detail:
     * the domain works with {@link Permissions} through {@link #getPermissions()}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "access", name = "pending_change_permissions",
            joinColumns = @JoinColumn(name = "change_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "scope", length = 50)
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 50)
    @Getter(AccessLevel.NONE)
    private Map<Scope, me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel> permissionLevels = new EnumMap<>(Scope.class);

    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private Long lockVersion;

    static PendingChange requested(long revision, ChangeKind kind, Instant requestedAt,
                                   String apiKeyName, Permissions permissions, Instant expiresAt) {
        PendingChange change = new PendingChange();
        change.changeId = UuidCreator.getTimeOrderedEpoch();
        change.revision = revision;
        change.kind = kind;
        change.requestedAt = requestedAt;
        change.status = ChangeStatus.PENDING;
        change.apiKeyName = apiKeyName;
        change.permissionLevels.putAll(permissions.asMap());
        change.expiresAt = expiresAt;
        return change;
    }

    /** The permissions the key will have once this change is confirmed. */
    public Permissions getPermissions() {
        return Permissions.of(permissionLevels);
    }

    boolean isPending() {
        return status == ChangeStatus.PENDING;
    }

    void fail(FailureReason reason) {
        this.status = ChangeStatus.FAILED;
        this.failureReason = reason;
    }
}
