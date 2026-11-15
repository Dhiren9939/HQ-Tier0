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
 * The API key aggregate. It owns every rule about how a key is issued, changed, confirmed by the
 * data plane, failed, dismissed and deleted. Callers state intent through its methods; there are no setters.
 *
 * <p>The attributes held directly on this class (name, permissions, expiry) are the <em>confirmed</em> ones.
 * A requested change lives in {@link #pendingChange} until the data plane confirms it.</p>
 *
 * <p>Every command that requests a revision also registers the matching domain event. The persistence layer
 * collects them with {@link #pullDomainEvents()} after saving the key and maps them to the published contract.</p>
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
     * Confirmed permissions as scope-to-level rows. Persistence detail: the domain works with
     * {@link Permissions} through {@link #getPermissions()}.
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

    /** Set when a delete is confirmed; the key then stays as a tombstone until dismissed. */
    private Instant deletedAt;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_change_id", unique = true)
    private PendingChange pendingChange;

    /** Latest requested revision: 1 at issuance, +1 per change. Carried as the {@code version} of events. */
    @Column(nullable = false)
    private long revision;

    /** Highest revision confirmed by the data plane. 0 means the key was never provisioned. */
    @Column(nullable = false)
    private long confirmedRevision;

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

    /** Issues a new key at revision 1 with a pending creation. */
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
        key.confirmedRevision = 0;
        key.pendingChange = PendingChange.requested(1, ChangeKind.CREATE, createdAt,
                apiKeyName, permissions, expiresAt);
        key.pendingEvents.add(key.createdEvent(createdAt));
        return key;
    }

    /**
     * Requests an update. Returns {@code false}, changing nothing, when no requested value differs
     * from the current one.
     */
    public boolean requestUpdate(ApiKeyChanges changes, Instant now) {
        assertChangeable("update");

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

        revision++;
        pendingChange = PendingChange.requested(revision, ChangeKind.UPDATE, now, name, updatedPermissions, expiry);
        pendingEvents.add(updatedEvent(now));
        return true;
    }

    /** Requests deletion of the key. */
    public void requestDeletion(Instant now) {
        assertChangeable("delete");

        revision++;
        pendingChange = PendingChange.requested(revision, ChangeKind.DELETE, now,
                apiKeyName, getPermissions(), expiresAt);
        pendingEvents.add(deletedEvent(now));
    }

    /** The data plane confirmed the pending change at {@code revision}. */
    public void confirm(long revision, Instant now) {
        PendingChange change = requirePendingAt(revision);

        switch (change.getKind()) {
            case CREATE -> {
            }
            case UPDATE -> {
                this.apiKeyName = change.getApiKeyName();
                assignPermissions(change.getPermissions());
                this.expiresAt = change.getExpiresAt();
            }
            case DELETE -> this.deletedAt = now;
        }
        this.confirmedRevision = change.getRevision();
        this.pendingChange = null;
    }

    /**
     * The pending change at {@code revision} failed for {@code reason}. The data plane may have applied it anyway,
     * so the compensating command is registered at once, at the next revision, restating the confirmed state:
     * delete for a failed creation, update for a failed update, create (restore) for a failed delete. The create
     * carries the secret hash, so it also works when the data plane already removed the key.
     */
    public void reject(long revision, FailureReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason must not be null");
        failAndCompensate(requirePendingAt(revision), reason, now);
    }

    /**
     * The owner cancels the change that is still pending. It fails with {@link FailureReason#CANCELLED_BY_USER}
     * and, like any failure, registers its compensating command at once: the data plane may already have applied
     * the change.
     */
    public void cancelPendingChange(Instant now) {
        if (pendingChange == null || !pendingChange.isPending())
            throw new ApiKeyUpdateFailure(UpdateFailureCode.NO_PENDING_CHANGE, "API key has no pending change to cancel");
        failAndCompensate(pendingChange, FailureReason.CANCELLED_BY_USER, now);
    }

    private void failAndCompensate(PendingChange change, FailureReason reason, Instant now) {
        change.fail(reason);

        this.revision++;
        pendingEvents.add(switch (change.getKind()) {
            case CREATE -> deletedEvent(now, desiredState());
            case UPDATE -> updatedEvent(now, confirmedState());
            case DELETE -> createdEvent(now, confirmedState());
        });
    }

    /**
     * The owner acknowledges a failed or finished key. Tells the caller whether the key must be removed.
     * It only records that the owner has seen the state; the compensating commands were already registered
     * when the change failed, so dismissing registers nothing.
     */
    public DismissResult dismiss() {
        return switch (lifecycle()) {
            case CHANGE_FAILED -> {
                pendingChange = null;
                yield DismissResult.CLEARED;
            }
            case PROVISIONING_FAILED, DELETED -> DismissResult.REMOVED;
            default -> throw new ApiKeyUpdateFailure(UpdateFailureCode.NOTHING_TO_DISMISS, "API key has nothing to dismiss");
        };
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

    /** The confirmed permissions. */
    public Permissions getPermissions() {
        return Permissions.of(permissionLevels);
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    /**
     * Where the key stands at {@code now}. Expiry comes from the clock, so it is derived here and not stored:
     * a key whose confirmed expiry has passed is {@link ApiKeyState#EXPIRED}, whatever change is pending on it.
     */
    public ApiKeyState state(Instant now) {
        ApiKeyState lifecycle = lifecycle();
        boolean confirmed = lifecycle == ApiKeyState.ACTIVE
                || lifecycle == ApiKeyState.CHANGE_PENDING
                || lifecycle == ApiKeyState.CHANGE_FAILED;
        return confirmed && expiresAt != null && expiresAt.isBefore(now) ? ApiKeyState.EXPIRED : lifecycle;
    }

    /**
     * Verification only trusts confirmed state: never provisioning, deleted or expired keys. A pending or failed
     * change does not stop the key working with its confirmed attributes.
     */
    public boolean canAuthenticate(Instant now) {
        return switch (state(now)) {
            case ACTIVE, CHANGE_PENDING, CHANGE_FAILED -> true;
            case PROVISIONING, PROVISIONING_FAILED, EXPIRED, DELETED -> false;
        };
    }

    /** The state ignoring the clock: what the rules about changing and dismissing a key depend on. */
    private ApiKeyState lifecycle() {
        if (deletedAt != null)
            return ApiKeyState.DELETED;
        if (pendingChange == null)
            return ApiKeyState.ACTIVE;
        boolean pending = pendingChange.isPending();
        if (pendingChange.getKind() == ChangeKind.CREATE)
            return pending ? ApiKeyState.PROVISIONING : ApiKeyState.PROVISIONING_FAILED;
        return pending ? ApiKeyState.CHANGE_PENDING : ApiKeyState.CHANGE_FAILED;
    }

    /** The confirmed state, ignoring any pending or failed change. */
    private DesiredState confirmedState() {
        return new DesiredState(apiKeyName, getPermissions(), expiresAt);
    }

    /** What this key will look like once its pending change (if any) is confirmed. */
    public DesiredState desiredState() {
        return pendingChange != null
                ? new DesiredState(pendingChange.getApiKeyName(), pendingChange.getPermissions(), pendingChange.getExpiresAt())
                : new DesiredState(apiKeyName, getPermissions(), expiresAt);
    }

    // ---------------------------------------------------------------------------------------------

    private ApiKeyCreated createdEvent(Instant now) {
        return createdEvent(now, desiredState());
    }

    private ApiKeyCreated createdEvent(Instant now, DesiredState desired) {
        return new ApiKeyCreated(desired.apiKeyName(), apiKeyId, revision, now,
                userId, accessId, accessSecretHash, desired.permissions(), createdAt, expiresAt);
    }

    private ApiKeyUpdated updatedEvent(Instant now) {
        return updatedEvent(now, desiredState());
    }

    private ApiKeyUpdated updatedEvent(Instant now, DesiredState desired) {
        return new ApiKeyUpdated(desired.apiKeyName(), apiKeyId, revision, now,
                userId, accessId, desired.permissions(), createdAt, expiresAt);
    }

    private ApiKeyDeleted deletedEvent(Instant now) {
        return deletedEvent(now, desiredState());
    }

    private ApiKeyDeleted deletedEvent(Instant now, DesiredState desired) {
        return new ApiKeyDeleted(desired.apiKeyName(), apiKeyId, revision, now,
                userId, accessId, desired.permissions(), createdAt, expiresAt);
    }

    private void assignPermissions(Permissions permissions) {
        permissionLevels.clear();
        permissionLevels.putAll(permissions.asMap());
    }

    private void assertChangeable(String action) {
        switch (lifecycle()) {
            case ACTIVE -> {
            }
            case PROVISIONING ->
                    throw new ApiKeyUpdateFailure(UpdateFailureCode.API_KEY_CREATION_PENDING, "Can't " + action + " an API key with pending creation");
            case PROVISIONING_FAILED ->
                    throw new ApiKeyUpdateFailure(UpdateFailureCode.API_KEY_CREATION_FAILED, "Can't " + action + " an API key whose creation failed; dismiss it first");
            case CHANGE_PENDING ->
                    throw new ApiKeyUpdateFailure(UpdateFailureCode.API_KEY_CHANGE_PENDING, "Can't " + action + " an API key with pending changes");
            case CHANGE_FAILED ->
                    throw new ApiKeyUpdateFailure(UpdateFailureCode.API_KEY_CHANGE_FAILED, "Can't " + action + " an API key with a failed change; dismiss it first");
            case DELETED ->
                    throw new ApiKeyUpdateFailure(UpdateFailureCode.API_KEY_DELETED, "Can't " + action + " a deleted API key");
            case EXPIRED -> throw new IllegalStateException("lifecycle() does not depend on the clock");
        }
    }

    private PendingChange requirePendingAt(long revision) {
        if (pendingChange == null || !pendingChange.isPending())
            throw new ApiKeyUpdateFailure(UpdateFailureCode.NO_PENDING_CHANGE, "API key has no pending change to resolve");
        if (pendingChange.getRevision() != revision)
            throw new ApiKeyUpdateFailure(UpdateFailureCode.REVISION_MISMATCH, "Revision " + revision + " is not the pending revision "
                    + pendingChange.getRevision());
        return pendingChange;
    }
}
