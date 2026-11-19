package me.dhiren9939.backend.ingestion.application.usecase.projection;

import me.dhiren9939.backend.ingestion.domain.projection.ApiKeySnapshot;
import me.dhiren9939.backend.ingestion.domain.projection.ProjectedApiKey;
import me.dhiren9939.backend.ingestion.domain.projection.ProjectionResult;
import me.dhiren9939.backend.ingestion.domain.repositories.ProjectedApiKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static me.dhiren9939.backend.ingestion.domain.projection.Snapshots.KEY;
import static me.dhiren9939.backend.ingestion.domain.projection.Snapshots.active;
import static me.dhiren9939.backend.ingestion.domain.projection.Snapshots.deleted;
import static org.assertj.core.api.Assertions.assertThat;

/** One snapshot per call: a new key is created, a known key applies it, and only changes are saved. */
class ProjectApiKeyHandlerTest {

    private InMemoryProjectedApiKeys store;
    private ProjectApiKeyHandler handler;

    @BeforeEach
    void setUp() {
        store = new InMemoryProjectedApiKeys();
        handler = new ProjectApiKeyHandler(store);
    }

    private ProjectionResult project(ApiKeySnapshot snapshot) {
        return handler.handle(new ProjectApiKeyCommand(UUID.randomUUID(), snapshot));
    }

    @Test
    void aNewKeyIsCreatedAndSaved() {
        assertThat(project(active(1))).isEqualTo(ProjectionResult.CREATED);

        assertThat(store.saves).hasSize(1);
        assertThat(store.findById(KEY)).map(ProjectedApiKey::version).contains(1L);
    }

    @Test
    void aNewKeySeenFirstAsADeleteIsSavedAsATombstone() {
        assertThat(project(deleted(3))).isEqualTo(ProjectionResult.DELETED);

        assertThat(store.findById(KEY)).map(ProjectedApiKey::isDeleted).contains(true);
    }

    @Test
    void aNewerSnapshotUpdatesTheSavedKey() {
        project(active(1));

        assertThat(project(active(2))).isEqualTo(ProjectionResult.UPDATED);
        assertThat(store.saves).hasSize(2);
        assertThat(store.findById(KEY)).map(ProjectedApiKey::accessId).contains("acc_v2");
    }

    @Test
    void aDuplicateOrAStaleSnapshotIsNotSaved() {
        project(active(2));

        assertThat(project(active(2))).isEqualTo(ProjectionResult.DUPLICATE);
        assertThat(project(active(1))).isEqualTo(ProjectionResult.STALE);
        assertThat(store.saves).hasSize(1);
    }

    @Test
    void aDeleteIsSavedAndTheKeyNeverComesBack() {
        project(active(1));

        assertThat(project(deleted(2))).isEqualTo(ProjectionResult.DELETED);
        assertThat(project(active(1))).isEqualTo(ProjectionResult.STALE);
        assertThat(project(active(3))).isEqualTo(ProjectionResult.ALREADY_DELETED);

        assertThat(store.saves).hasSize(2);
        assertThat(store.findById(KEY)).map(ProjectedApiKey::isDeleted).contains(true);
    }

    @Test
    void aRedeliveredStreamEndsInTheSameStateAsTheOriginal() {
        List<ApiKeySnapshot> stream = List.of(active(1), active(2), deleted(3));
        stream.forEach(this::project);
        ApiKeySnapshot once = store.findById(KEY).orElseThrow().snapshot();

        stream.forEach(this::project);

        assertThat(store.findById(KEY).orElseThrow().snapshot()).isEqualTo(once);
        assertThat(store.saves).hasSize(3);
    }

    /** Keeps keys in a map and counts saves. */
    private static final class InMemoryProjectedApiKeys implements ProjectedApiKeys {
        private final Map<UUID, ProjectedApiKey> keys = new HashMap<>();
        private final List<UUID> saves = new ArrayList<>();

        @Override
        public Optional<ProjectedApiKey> findById(UUID apiKeyId) {
            return Optional.ofNullable(keys.get(apiKeyId));
        }

        @Override
        public void save(ProjectedApiKey key) {
            keys.put(key.apiKeyId(), key);
            saves.add(key.apiKeyId());
        }
    }
}
