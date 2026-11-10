package me.dhiren9939.backend.integration;

import me.dhiren9939.backend.access.application.port.in.ApiKeyView;
import me.dhiren9939.backend.access.application.port.in.ConfirmChangeCommand;
import me.dhiren9939.backend.access.application.port.in.ConfirmChangeUseCase;
import me.dhiren9939.backend.access.application.port.in.DeleteApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.DismissApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.GetApiKeyQuery;
import me.dhiren9939.backend.access.application.port.in.GetApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.IssueApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.IssueApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.IssuedApiKeyResult;
import me.dhiren9939.backend.access.application.port.in.RejectChangeUseCase;
import me.dhiren9939.backend.access.application.port.in.UpdateApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.VerifyApiKeyUseCase;
import me.dhiren9939.backend.access.domain.Lifetime;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Base for Access tests that exercise the real application against PostgreSQL. Every test starts with
 * empty Access tables and a clock that follows the system clock.
 */
@Import(TestClockConfig.class)
@AutoConfigureMockMvc
public abstract class AccessIntegrationTest extends PostgresIntegrationTest {

    protected static final String VERIFY_FAILURE = "Could not verify the ApiKey";

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;

    @Autowired protected IssueApiKeyUseCase issueApiKey;
    @Autowired protected GetApiKeyUseCase getApiKey;
    @Autowired protected UpdateApiKeyUseCase updateApiKey;
    @Autowired protected DeleteApiKeyUseCase deleteApiKey;
    @Autowired protected DismissApiKeyUseCase dismissApiKey;
    @Autowired protected ConfirmChangeUseCase confirmChange;
    @Autowired protected RejectChangeUseCase rejectChange;
    @Autowired protected VerifyApiKeyUseCase verifyApiKey;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE access.outbox, access.api_key_permissions, access.pending_change_permissions, "
                + "access.api_keys, access.pending_changes");
        clock.reset();
    }

    protected static Permissions ingestionWrite() {
        return Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE);
    }

    protected IssuedApiKeyResult issue(UUID owner, String name, Lifetime lifetime, Permissions permissions) {
        return issueApiKey.handle(new IssueApiKeyCommand(owner, name, lifetime, permissions));
    }

    /** Issues a key and has the data plane confirm its creation, so it is ACTIVE. */
    protected IssuedApiKeyResult issueActive(UUID owner, String name, Lifetime lifetime, Permissions permissions) {
        IssuedApiKeyResult issued = issue(owner, name, lifetime, permissions);
        confirmChange.handle(new ConfirmChangeCommand(issued.key().apiKeyId(), 1));
        return issued;
    }

    protected ApiKeyView view(UUID owner, UUID apiKeyId) {
        return getApiKey.handle(new GetApiKeyQuery(owner, apiKeyId));
    }

    protected long count(String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }

    /** What the outbox holds, oldest revision first. */
    protected List<OutboxRow> outbox() {
        return jdbc.query("""
                select event_type, change_version, access_secret_hash, permissions, api_key_name,
                       occurred_at, emitted_at, api_key_id
                from access.outbox order by change_version, event_type
                """, (rs, i) -> new OutboxRow(
                rs.getString("event_type"),
                rs.getLong("change_version"),
                rs.getString("access_secret_hash"),
                rs.getString("permissions"),
                rs.getString("api_key_name"),
                rs.getObject("occurred_at"),
                rs.getObject("emitted_at"),
                rs.getObject("api_key_id", UUID.class)));
    }

    protected record OutboxRow(String eventType, long version, String accessSecretHash, String permissions,
                               String apiKeyName, Object occurredAt, Object emittedAt, UUID apiKeyId) {
    }
}
