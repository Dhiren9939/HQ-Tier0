package me.dhiren9939.backend.access.domain.aggregates.apikey;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.access.domain.exceptions.UpdateFailureCode;
import me.dhiren9939.backend.access.domain.events.ApiKeyCreated;
import me.dhiren9939.backend.access.domain.events.ApiKeyDeleted;
import me.dhiren9939.backend.access.domain.events.ApiKeyEvent;
import me.dhiren9939.backend.access.domain.events.ApiKeyUpdated;

import java.time.Instant;
import java.util.*;

/**
 * The API key aggregate. It owns every rule about how a key is issued, changed, dismissed and deleted; callers
 * state intent through its methods, there are no setters.
 *
 * <p>HQ is the source of truth. Every command applies to the key at once, in the caller's transaction, and
 * registers the matching domain event; the persistence layer collects them with {@link #pullDomainEvents()} after
 * saving the key and maps them to the published contract. Nothing waits on the data plane: it is expected to
 * conform to the events, which carry a per-key {@code version} for ordering and idempotency.</p>
 *
 * <p>A delete is not removed at once: the key stays as a {@code DELETED} tombstone until the owner dismisses it,
 * so the removal stays visible.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "api_keys", schema = "access")
public class ApiKey {

    @Id
    private UUID apiKeyId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 256)
    private String accessId;

    @Column(nullable = false, length = 256)
    private String accessSecretHash;

    @Column(nullable = false, length = 256)
    private String apiKeyName;

    /**
     * Permissions as scope-to-level rows. Persistence detail: the domain works with {@link Permissions}
     * through {@link #getPermissions()}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "access", name = "api_key_permissions",
            joinColumns = @JoinColumn(name = "api_key_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "scope", length = 50)
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 50)
    @Getter(AccessLevel.NONE)
    private Map<Scope, me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel> permissionLevels = new EnumMap<>(Scope.class);

    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

    /** Set when the key is deleted; it then stays as a tombstone until dismissed. */
    private Instant deletedAt;

    /** The key's own event sequence: 1 at issuance, +1 per change. Carried as the {@code version} of events. */
    @Column(nullable = false)
    private long revision;

    @Version
    @Column(nullable = false)
    private Long lockVersion;

    /** Events registered by commands and not yet collected. Not persisted. */
    @Transient
    @Getter(AccessLevel.NONE)
    private final List<ApiKeyEvent> pendingEvents = new ArrayList<>();

    // ---------------------------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------------------------

    /** Issues a new key at version 1; it is active at once and the creation event is registered. */
    public static ApiKey issue(UUID userId, String apiKeyName, IssuedCredential credential,
                               Permissions permissions, Instant createdAt, Instant expiresAt) {
        ApiKey key = new ApiKey();
        key.apiKeyId = UuidCreator.getTimeOrderedEpoch();
        key.userId = userId;
        key.apiKeyName = apiKeyName;
        key.accessId = credential.accessId();
        key.accessSecretHash = credential.secretHash();
        key.assignPermissions(permissions);
        key.createdAt = createdAt;
        key.expiresAt = expiresAt;
        key.revision = 1;
        key.pendingEvents.add(key.createdEvent(createdAt));
        return key;
    }

    /**
     * Applies the requested changes at once. Returns {@code false}, changing nothing, when no requested value
     * differs from the current one.
     */
    public boolean update(ApiKeyChanges changes, Instant now) {
        assertNotDeleted("update");

        String name = apiKeyName;
        Instant expiry = expiresAt;
        Permissions updatedPermissions = getPermissions();
        boolean changed = false;

        if (changes.name() != null && !name.equals(changes.name())) {
            name = changes.name();
            changed = true;
        }
        if (changes.lifetime() != null) {
            Instant newExpiry = changes.lifetime().expiryFrom(now);
            if (!Objects.equals(expiry, newExpiry)) {
                expiry = newExpiry;
                changed = true;
            }
        }
        if (changes.permissions() != null) {
            Permissions merged = changes.permissions().applyTo(getPermissions());
            if (!merged.equals(getPermissions())) {
                updatedPermissions = merged;
                changed = true;
            }
        }

        if (!changed)
            return false;

        this.apiKeyName = name;
        this.expiresAt = expiry;
        assignPermissions(updatedPermissions);
        revision++;
        pendingEvents.add(updatedEvent(now));
        return true;
    }

    /** Deletes the key. It stays as a tombstone until dismissed; the deletion event is registered at once. */
    public void delete(Instant now) {
        assertNotDeleted("delete");

        revision++;
        deletedAt = now;
        pendingEvents.add(deletedEvent(now));
    }

    /**
     * The owner acknowledges the deleted tombstone. Tells the caller whether the key must be removed. It only
     * records that the owner has seen the state; the deletion event was registered when the key was deleted,
     * so dismissing registers nothing.
     */
    public void dismiss() {
        if (deletedAt == null)
            throw new ApiKeyUpdateFailure(UpdateFailureCode.NOTHING_TO_DISMISS, "API key has nothing to dismiss");
    }

    /** Returns the events registered since the last call, in order, and forgets them. */
    public List<ApiKeyEvent> pullDomainEvents() {
        List<ApiKeyEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    // ---------------------------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------------------------

    /** The permissions. */
    public Permissions getPermissions() {
        return Permissions.of(permissionLevels);
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    /**
     * Where the key stands at {@code now}. Expiry comes from the clock, so it is derived here and not stored:
     * a key whose expiry has passed is {@link ApiKeyState#EXPIRED}.
     */
    public ApiKeyState state(Instant now) {
        if (deletedAt != null)
            return ApiKeyState.DELETED;
        return expiresAt != null && expiresAt.isBefore(now) ? ApiKeyState.EXPIRED : ApiKeyState.ACTIVE;
    }

    /** Verification trusts HQ's state outright: deleted or expired keys do not authenticate, everything else does. */
    public boolean canAuthenticate(Instant now) {
        return state(now) == ApiKeyState.ACTIVE;
    }

    // ---------------------------------------------------------------------------------------------

    private ApiKeyCreated createdEvent(Instant now) {
        return new ApiKeyCreated(apiKeyName, apiKeyId, revision, now,
                userId, accessId, accessSecretHash, getPermissions(), createdAt, expiresAt);
    }

    private ApiKeyUpdated updatedEvent(Instant now) {
        return new ApiKeyUpdated(apiKeyName, apiKeyId, revision, now,
                userId, accessId, getPermissions(), createdAt, expiresAt);
    }

    private ApiKeyDeleted deletedEvent(Instant now) {
        return new ApiKeyDeleted(apiKeyName, apiKeyId, revision, now,
                userId, accessId, getPermissions(), createdAt, expiresAt);
    }

    private void assignPermissions(Permissions permissions) {
        permissionLevels.clear();
        permissionLevels.putAll(permissions.asMap());
    }

    private void assertNotDeleted(String action) {
        if (deletedAt != null)
            throw new ApiKeyUpdateFailure(UpdateFailureCode.API_KEY_DELETED, "Can't " + action + " a deleted API key");
    }
}
