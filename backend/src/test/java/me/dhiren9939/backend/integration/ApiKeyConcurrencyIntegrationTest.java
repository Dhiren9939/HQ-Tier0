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
 * Several owners' threads update the same key at the same moment. Changes apply immediately and in
 * sequence: every writer either lands its change or loses the optimistic-lock race and retries, so
 * the event versions stay unique and monotonic and the last event describes the key's final state.
 */
class ApiKeyConcurrencyIntegrationTest extends AccessIntegrationTest {

    private static final int THREADS = 8;

    private final UUID owner = UUID.randomUUID();

    @RepeatedTest(5)
    @DisplayName("concurrent updates of one key: versions stay unique and monotonic, the last event wins")
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
            List<Throwable> unexpected = new ArrayList<>();
            for (Future<ApiKeyView> attempt : attempts) {
                try {
                    attempt.get(60, TimeUnit.SECONDS);
                    succeeded++;
                } catch (java.util.concurrent.ExecutionException failure) {
                    if (failure.getCause() instanceof ApiKeyUpdateFailure)
                        succeeded += 0; // a writer may lose the race and exhaust its retries
                    else
                        unexpected.add(failure.getCause());
                }
            }

            assertThat(unexpected).as("only ApiKeyUpdateFailure is an acceptable loser outcome").isEmpty();
            assertThat(succeeded).as("at least one writer lands its change").isGreaterThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.ACTIVE);
        List<OutboxRow> rows = outbox();
        assertThat(rows).extracting(OutboxRow::version)
                .as("event versions are unique and strictly increasing")
                .doesNotHaveDuplicates()
                .isSorted()
                .startsWith(1L);
        long updates = rows.stream().filter(row -> row.eventType().equals("apiKey.projection.update")).count();
        assertThat(updates).as("one update event per landed change").isEqualTo(rows.size() - 1);
        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class))
                .as("the revision matches the number of landed changes")
                .isEqualTo(1 + updates);
        String eventName = rows.getLast().apiKeyName();
        assertThat(eventName).as("the last event describes the change that actually won")
                .isEqualTo(jdbc.queryForObject("select api_key_name from access.api_keys where api_key_id = ?", String.class, id));
    }
}
