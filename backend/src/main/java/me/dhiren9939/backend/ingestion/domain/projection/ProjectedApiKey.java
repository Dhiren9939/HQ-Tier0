package me.dhiren9939.backend.ingestion.domain.projection;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Ingestion's copy of one API key, built from the snapshots Access publishes.
 *
 * <p>Snapshots can arrive twice (the relay delivers at least once) or late, so the key only ever moves forward:</p>
 * <ul>
 *   <li>the highest {@code version} wins; an equal one is a duplicate and a lower one is stale, both ignored;</li>
 *   <li>a delete leaves a tombstone that keeps its version, so a late create cannot bring the key back;</li>
 *   <li>a deleted key stays deleted: Access never revives one, so a newer non-delete snapshot is ignored.</li>
 * </ul>
 * Whatever order the snapshots arrive in, the key ends in the same state.
 */
public class ProjectedApiKey {

    /** The latest snapshot applied; the key's whole state. */
    private ApiKeySnapshot state;

    private ProjectedApiKey(ApiKeySnapshot state) {
        this.state = state;
    }

    /** The key as its first known snapshot describes it, which may already be a delete. */
    public static ProjectedApiKey first(ApiKeySnapshot snapshot) {
        if (snapshot == null)
            throw new IllegalArgumentException("API key snapshot is required");
        return new ProjectedApiKey(snapshot);
    }

    /** What the first snapshot did: CREATED, or DELETED when the key was first seen as a delete. */
    public ProjectionResult firstResult() {
        return isDeleted() ? ProjectionResult.DELETED : ProjectionResult.CREATED;
    }

    /** Applies a later snapshot of this key, following the rules in the class comment. */
    public ProjectionResult apply(ApiKeySnapshot next) {
        if (next == null)
            throw new IllegalArgumentException("API key snapshot is required");
        if (!next.apiKeyId().equals(apiKeyId()))
            throw new IllegalArgumentException(
                    "API key snapshot of key " + next.apiKeyId() + " cannot be applied to key " + apiKeyId());

        if (next.version() == version())
            return ProjectionResult.DUPLICATE;
        if (next.version() < version())
            return ProjectionResult.STALE;
        if (isDeleted())
            return ProjectionResult.ALREADY_DELETED;

        state = next;
        return isDeleted() ? ProjectionResult.DELETED : ProjectionResult.UPDATED;
    }

    public UUID apiKeyId() {
        return state.apiKeyId();
    }

    public long version() {
        return state.version();
    }

    public boolean isDeleted() {
        return state.status() == KeyStatus.DELETED;
    }

    public UUID userId() {
        return state.userId();
    }

    public String accessId() {
        return state.accessId();
    }

    public String accessSecretHash() {
        return state.accessSecretHash();
    }

    public IngestionAccess ingestionAccess() {
        return state.ingestionAccess();
    }

    /** Empty when the key never expires. */
    public Optional<Instant> expiresAt() {
        return state.expiresAt();
    }

    /** The latest snapshot applied. */
    public ApiKeySnapshot snapshot() {
        return state;
    }
}
