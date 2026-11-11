package me.dhiren9939.backend.integration;

import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.dismiss.DismissApiKeyCommand;
import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeRejected;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * If the outbox row cannot be written, the key change must not survive. The outbox write is made to fail
 * for real with a database trigger that refuses rows for a key named {@code boom}.
 */
class ApiKeyAtomicityIntegrationTest extends AccessIntegrationTest {

    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void installFailingOutboxTrigger() {
        jdbc.execute("""
                create or replace function access.refuse_boom() returns trigger as $$
                begin
                    if new.api_key_name = 'boom' then
                        raise exception 'outbox write refused for test';
                    end if;
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbc.execute("drop trigger if exists refuse_boom on access.outbox");
        jdbc.execute("create trigger refuse_boom before insert on access.outbox "
                + "for each row execute function access.refuse_boom()");
    }

    @AfterEach
    void removeTrigger() {
        jdbc.execute("drop trigger if exists refuse_boom on access.outbox");
    }

    @Test
    @DisplayName("issue: a failing outbox write leaves no key, no pending change and no permission rows")
    void issueRollsBack() {
        assertThatThrownBy(() -> issue(owner, "boom", Lifetime.NEVER, ingestionWrite()))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("access.api_keys")).isZero();
        assertThat(count("access.pending_changes")).isZero();
        assertThat(count("access.pending_change_permissions")).isZero();
        assertThat(count("access.api_key_permissions")).isZero();
        assertThat(count("access.outbox")).isZero();
    }

    @Test
    @DisplayName("update: a failing outbox write leaves the key at its old revision with no pending change")
    void updateRollsBack() {
        UUID id = issueActive(owner, "stable", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();
        int events = outbox().size();

        assertThatThrownBy(() -> updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "boom", null, null)))
                .isInstanceOf(RuntimeException.class);

        ApiKeyView key = view(owner, id);
        assertThat(key.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(key.apiKeyName()).isEqualTo("stable");
        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class)).isEqualTo(1);
        assertThat(count("access.pending_changes")).isZero();
        assertThat(outbox()).hasSize(events);
    }

    @Test
    @DisplayName("delete: a failing outbox write leaves the key ACTIVE with no pending delete")
    void deleteRollsBack() {
        // the delete event copies the key's name, so a key named `boom` cannot be deleted
        UUID id = jdbcIssueActiveNamedBoom();
        int events = outbox().size();

        assertThatThrownBy(() -> deleteApiKey.handle(new DeleteApiKeyCommand(owner, id)))
                .isInstanceOf(RuntimeException.class);

        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(count("access.pending_changes")).isZero();
        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class)).isEqualTo(1);
        assertThat(outbox()).hasSize(events);
    }

    @Test
    @DisplayName("rejecting a creation: a failing outbox write keeps the change pending")
    void rejectCreationRollsBack() {
        UUID id = jdbcIssueNamedBoom();
        int events = outbox().size();

        assertThatThrownBy(() -> rejectChange.handle(new ApiKeyChangeRejected(id, 1, "data-plane", FailureReason.REJECTED)))
                .isInstanceOf(RuntimeException.class);

        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.PROVISIONING);
        assertThat(jdbc.queryForObject("select status from access.pending_changes", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class)).isEqualTo(1);
        assertThat(outbox()).hasSize(events);
    }

    /** Creates an ACTIVE key named `boom` while the trigger is temporarily off (creation itself would be refused). */
    private UUID jdbcIssueActiveNamedBoom() {
        jdbc.execute("alter table access.outbox disable trigger refuse_boom");
        try {
            return issueActive(owner, "boom", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();
        } finally {
            jdbc.execute("alter table access.outbox enable trigger refuse_boom");
        }
    }

    private UUID jdbcIssueNamedBoom() {
        jdbc.execute("alter table access.outbox disable trigger refuse_boom");
        try {
            return issue(owner, "boom", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();
        } finally {
            jdbc.execute("alter table access.outbox enable trigger refuse_boom");
        }
    }
}
