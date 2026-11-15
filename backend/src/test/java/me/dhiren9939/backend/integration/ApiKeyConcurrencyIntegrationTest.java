package me.dhiren9939.backend.integration;

import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Several owners' threads update the same key at the same moment. Exactly one change may win; the others
 * must fail with {@link ApiKeyUpdateFailure} (HTTP 409), and nothing may be half-written.
 */
class ApiKeyConcurrencyIntegrationTest extends AccessIntegrationTest {

    private static final int THREADS = 8;

    private final UUID owner = UUID.randomUUID();

    @RepeatedTest(5)
    @DisplayName("concurrent updates of one key: exactly one wins, the rest get ApiKeyUpdateFailure")
    void concurrentUpdates() throws Exception {
        UUID id = issueActive(owner, "contended", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<ApiKeyView>> attempts = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                String name = "writer-" + i;
                Callable<ApiKeyView> task = () -> {
                    ready.countDown();
                    go.await();
                    return updateApiKey.handle(new UpdateApiKeyCommand(owner, id, name, null, null));
                };
                attempts.add(pool.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int succeeded = 0;
            int conflicted = 0;
            List<Throwable> unexpected = new ArrayList<>();
            for (Future<ApiKeyView> attempt : attempts) {
                try {
                    attempt.get(60, TimeUnit.SECONDS);
                    succeeded++;
                } catch (java.util.concurrent.ExecutionException failure) {
                    if (failure.getCause() instanceof ApiKeyUpdateFailure)
                        conflicted++;
                    else
                        unexpected.add(failure.getCause());
                }
            }

            assertThat(unexpected).as("only ApiKeyUpdateFailure is an acceptable loser outcome").isEmpty();
            assertThat(succeeded).isEqualTo(1);
            assertThat(conflicted).isEqualTo(THREADS - 1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.CHANGE_PENDING);
        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class)).isEqualTo(2);
        assertThat(count("access.pending_changes")).isEqualTo(1);
        assertThat(count("access.outbox where event_type = 'apiKey.projection.update'")).isEqualTo(1);
        assertThat(outbox()).extracting(OutboxRow::version).containsExactly(1L, 2L);
        String pendingName = jdbc.queryForObject("select api_key_name from access.pending_changes", String.class);
        String eventName = jdbc.queryForObject(
                "select api_key_name from access.outbox where event_type = 'apiKey.projection.update'", String.class);
        assertThat(eventName).as("the event describes the change that actually won").isEqualTo(pendingName);
    }
}
