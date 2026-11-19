package me.dhiren9939.backend.ingestion.domain.projection;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Valid snapshots for tests. */
public final class Snapshots {
    public static final UUID KEY = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final Instant EXPIRES = Instant.parse("2027-01-15T11:00:00Z");

    private Snapshots() {
    }

    /** The key, ACTIVE at {@code version}, with WRITE access and an access id that names the version. */
    public static ApiKeySnapshot active(long version) {
        return new ApiKeySnapshot(KEY, version, KeyStatus.ACTIVE, USER, "acc_v" + version, "hash_v" + version,
                IngestionAccess.WRITE, Optional.of(EXPIRES));
    }

    /** The key, DELETED at {@code version}. */
    public static ApiKeySnapshot deleted(long version) {
        return new ApiKeySnapshot(KEY, version, KeyStatus.DELETED, USER, "acc_v" + version, "hash_v" + version,
                IngestionAccess.WRITE, Optional.of(EXPIRES));
    }
}
