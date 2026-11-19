package me.dhiren9939.backend.integration;

import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.application.usecase.service.verify.VerifyApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.get.GetApiKeyQuery;
import me.dhiren9939.backend.access.application.usecase.user.get.GetApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssuedApiKeyResult;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyUseCase;
import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base for Access tests that exercise the real application against PostgreSQL. Every test starts with
 * empty Access tables and a clock that follows the system clock.
 */
@Import(TestClockConfig.class)
@AutoConfigureMockMvc
public abstract class AccessIntegrationTest extends PostgresIntegrationTest {


    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;

    @Autowired protected IssueApiKeyUseCase issueApiKey;
    @Autowired protected GetApiKeyUseCase getApiKey;
    @Autowired protected UpdateApiKeyUseCase updateApiKey;
    @Autowired protected DeleteApiKeyUseCase deleteApiKey;
    @Autowired protected VerifyApiKeyUseCase verifyApiKey;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE access.outbox, access.outbox_dead_letter, access.api_key_permissions, access.api_keys");
        clock.reset();
    }

    protected static Permissions ingestionWrite() {
        return Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE);
    }

    protected IssuedApiKeyResult issue(UUID owner, String name, Lifetime lifetime, Permissions permissions) {
        return issueApiKey.handle(new IssueApiKeyCommand(owner, name, lifetime, permissions));
    }

    /** Issues a key; with no handshake to wait for it is ACTIVE at once. */
    protected IssuedApiKeyResult issueActive(UUID owner, String name, Lifetime lifetime, Permissions permissions) {
        return issue(owner, name, lifetime, permissions);
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
                select event_id, event_type, change_version, occurred_at, emitted_at, api_key_id, payload::text as payload
                from access.outbox order by change_version, event_type
                """, (rs, i) -> new OutboxRow(
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getLong("change_version"),
                rs.getObject("occurred_at"),
                rs.getObject("emitted_at"),
                rs.getObject("api_key_id", UUID.class),
                JSON.readTree(rs.getString("payload"))));
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** An outbox row: the metadata columns, and the event JSON with a few of its fields read out for convenience. */
    protected record OutboxRow(UUID eventId, String eventType, long version, Object occurredAt, Object emittedAt,
                               UUID apiKeyId, JsonNode payload) {

        public String accessSecretHash() {
            return payload.get("accessSecretHash").asString();
        }

        public String apiKeyName() {
            return payload.get("apiKeyName").asString();
        }

        public Map<String, String> permissions() {
            Map<String, String> permissions = new LinkedHashMap<>();
            payload.get("permissions").properties().forEach(entry -> permissions.put(entry.getKey(), entry.getValue().asString()));
            return permissions;
        }
    }
}
