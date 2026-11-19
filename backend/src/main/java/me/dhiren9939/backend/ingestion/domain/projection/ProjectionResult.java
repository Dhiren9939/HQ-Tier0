package me.dhiren9939.backend.ingestion.domain.projection;

/** What applying one snapshot did to the projection. */
public enum ProjectionResult {
    /** First snapshot of the key; the key is now known. */
    CREATED,
    /** A newer snapshot replaced the key's state. */
    UPDATED,
    /** The key is now a tombstone: deleted, with the version of its delete. */
    DELETED,
    /** Same version as the projection already has: a redelivery. Nothing changed. */
    DUPLICATE,
    /** Older than the projection: it arrived late. Nothing changed. */
    STALE,
    /** Newer, but the key is already deleted and a deleted key never comes back. Nothing changed. */
    ALREADY_DELETED;

    /** True when the projection changed and must be saved. */
    public boolean changed() {
        return this == CREATED || this == UPDATED || this == DELETED;
    }
}
