package me.dhiren9939.backend.ingestion.domain.projection;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static me.dhiren9939.backend.ingestion.domain.projection.Snapshots.USER;
import static me.dhiren9939.backend.ingestion.domain.projection.Snapshots.active;
import static me.dhiren9939.backend.ingestion.domain.projection.Snapshots.deleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The key only moves forward: highest version wins, deletes are tombstones, a deleted key never comes back. */
class ProjectedApiKeyTest {

    @Test
    void theFirstActiveSnapshotCreatesTheKey() {
        ProjectedApiKey key = ProjectedApiKey.first(active(1));

        assertThat(key.firstResult()).isEqualTo(ProjectionResult.CREATED);
        assertThat(key.isDeleted()).isFalse();
        assertThat(key.version()).isEqualTo(1);
        assertThat(key.apiKeyId()).isEqualTo(Snapshots.KEY);
        assertThat(key.userId()).isEqualTo(USER);
        assertThat(key.accessId()).isEqualTo("acc_v1");
        assertThat(key.accessSecretHash()).isEqualTo("hash_v1");
        assertThat(key.ingestionAccess()).isEqualTo(IngestionAccess.WRITE);
        assertThat(key.expiresAt()).contains(Snapshots.EXPIRES);
    }

    @Test
    void aKeyFirstSeenAsADeleteIsATombstone() {
        ProjectedApiKey key = ProjectedApiKey.first(deleted(4));

        assertThat(key.firstResult()).isEqualTo(ProjectionResult.DELETED);
        assertThat(key.isDeleted()).isTrue();
        assertThat(key.version()).isEqualTo(4);
    }

    @Test
    void aHigherVersionReplacesEveryField() {
        ProjectedApiKey key = ProjectedApiKey.first(active(1));
        ApiKeySnapshot v2 = new ApiKeySnapshot(Snapshots.KEY, 2, KeyStatus.ACTIVE, USER, "acc_new", "hash_new",
                IngestionAccess.NONE, Optional.empty());

        assertThat(key.apply(v2)).isEqualTo(ProjectionResult.UPDATED);
        assertThat(key.snapshot()).isEqualTo(v2);
        assertThat(key.ingestionAccess()).isEqualTo(IngestionAccess.NONE);
        assertThat(key.expiresAt()).isEmpty();
    }

    @Test
    void theSameVersionAgainIsADuplicateAndChangesNothing() {
        ProjectedApiKey key = ProjectedApiKey.first(active(2));

        assertThat(key.apply(active(2))).isEqualTo(ProjectionResult.DUPLICATE);
        assertThat(key.snapshot()).isEqualTo(active(2));
    }

    @Test
    void aLowerVersionIsStaleAndChangesNothing() {
        ProjectedApiKey key = ProjectedApiKey.first(active(3));

        assertThat(key.apply(active(2))).isEqualTo(ProjectionResult.STALE);
        assertThat(key.snapshot()).isEqualTo(active(3));
    }

    @Test
    void snapshotsArrivingOutOfOrderEndAtTheHighestVersion() {
        ProjectedApiKey key = ProjectedApiKey.first(active(1));

        key.apply(active(3));
        key.apply(active(2));

        assertThat(key.version()).isEqualTo(3);
        assertThat(key.accessId()).isEqualTo("acc_v3");
    }

    @Test
    void aDeleteMakesATombstoneThatKeepsItsVersion() {
        ProjectedApiKey key = ProjectedApiKey.first(active(1));

        assertThat(key.apply(deleted(2))).isEqualTo(ProjectionResult.DELETED);
        assertThat(key.isDeleted()).isTrue();
        assertThat(key.version()).isEqualTo(2);
    }

    @Test
    void aReplayedCreateAfterTheDeleteCannotBringTheKeyBack() {
        ProjectedApiKey key = ProjectedApiKey.first(active(1));
        key.apply(deleted(2));

        assertThat(key.apply(active(1))).isEqualTo(ProjectionResult.STALE);
        assertThat(key.isDeleted()).isTrue();
    }

    @Test
    void aNewerSnapshotAfterTheDeleteIsIgnoredBecauseADeletedKeyStaysDeleted() {
        ProjectedApiKey key = ProjectedApiKey.first(active(1));
        key.apply(deleted(2));

        assertThat(key.apply(active(3))).isEqualTo(ProjectionResult.ALREADY_DELETED);
        assertThat(key.isDeleted()).isTrue();
        assertThat(key.version()).isEqualTo(2);
    }

    @Test
    void theSameDeleteAgainIsADuplicate() {
        ProjectedApiKey key = ProjectedApiKey.first(deleted(2));

        assertThat(key.apply(deleted(2))).isEqualTo(ProjectionResult.DUPLICATE);
    }

    @Test
    void refusesASnapshotOfAnotherKey() {
        ProjectedApiKey key = ProjectedApiKey.first(active(1));
        ApiKeySnapshot other = new ApiKeySnapshot(UUID.randomUUID(), 2, KeyStatus.ACTIVE, USER, "a", "h",
                IngestionAccess.WRITE, Optional.of(Instant.EPOCH));

        assertThatThrownBy(() -> key.apply(other))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be applied to key " + Snapshots.KEY);
        assertThat(key.snapshot()).isEqualTo(active(1));
    }

    @Test
    void refusesAMissingSnapshot() {
        assertThatThrownBy(() -> ProjectedApiKey.first(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectedApiKey.first(active(1)).apply(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyCreatedUpdatedAndDeletedChangeTheProjection() {
        assertThat(ProjectionResult.values())
                .filteredOn(ProjectionResult::changed)
                .containsExactlyInAnyOrder(ProjectionResult.CREATED, ProjectionResult.UPDATED, ProjectionResult.DELETED);
    }
}
