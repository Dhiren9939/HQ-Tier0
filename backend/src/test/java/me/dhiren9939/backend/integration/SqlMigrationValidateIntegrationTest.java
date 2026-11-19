package me.dhiren9939.backend.integration;

import me.dhiren9939.backend.access.application.usecase.service.verify.VerifyApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssuedApiKeyResult;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyUseCase;
import me.dhiren9939.backend.access.domain.aggregates.apikey.*;
import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the hand-written access migrations and the JPA mappings agree. A brand-new
 * database inside the throwaway container gets only those SQL files; the application then boots with
 * {@code ddl-auto=validate}, so Hibernate refuses to start on any mismatch, and a full key lifecycle runs
 * against the hand-written tables.
 */
@Tag("integration")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
class SqlMigrationValidateIntegrationTest {

    private static final String DATABASE = "migration_check";

    @DynamicPropertySource
    static void databaseBuiltFromTheSqlFile(DynamicPropertyRegistry registry) throws Exception {
        var postgres = PostgresIntegrationTest.POSTGRES; // starts the shared container
        try (Connection admin = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("drop database if exists " + DATABASE);
            statement.execute("create database " + DATABASE);
        }

        String url = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + DATABASE;
        try (Connection connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db.migration/access/V1__create_access_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db.migration/access/V2__drop_pending_changes.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db.migration/access/V3__remove_api_key_tombstones.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db.migration/access/V4__drop_outbox_change_columns.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db.migration/access/V5__outbox_relay.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db.migration/access/V6__outbox_payload_jsonb.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db.migration/access/V7__text_columns_256.sql"));
        }

        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Value("${spring.jpa.hibernate.ddl-auto}") String ddlAuto;
    @Autowired JdbcTemplate jdbc;
    @Autowired IssueApiKeyUseCase issueApiKey;
    @Autowired VerifyApiKeyUseCase verifyApiKey;
    @Autowired UpdateApiKeyUseCase updateApiKey;
    @Autowired DeleteApiKeyUseCase deleteApiKey;

    @Test
    @DisplayName("Hibernate validates the entities against the tables created by the SQL file alone")
    void mappingsMatchTheSqlFile() {
        assertThat(ddlAuto).isEqualTo("validate");
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo(DATABASE);
    }

    @Test
    @DisplayName("every instant column is `timestamp with time zone`, like the schema Hibernate generates")
    void instantColumnsAreTimestamptz() {
        // Hibernate's validator accepts `timestamp` for an Instant (it only compares name prefixes),
        // so this is checked explicitly.
        var notTimestamptz = jdbc.queryForList("""
                select table_name || '.' || column_name || ' is ' || data_type from information_schema.columns
                where table_schema = 'access' and column_name like '%\\_at' and data_type <> 'timestamp with time zone'
                """, String.class);
        assertThat(notTimestamptz).isEmpty();
    }

    @Test
    @DisplayName("an Instant written through the app reads back unchanged, whatever the session time zone")
    void instantsRoundTrip() {
        UUID owner = UUID.randomUUID();
        java.time.Instant before = java.time.Instant.now();
        IssuedApiKeyResult issued = issueApiKey.handle(
                new IssueApiKeyCommand(owner, "tz", Lifetime.ONE_DAY, Permissions.none()));
        java.time.Instant after = java.time.Instant.now();

        java.time.Instant createdAt = issued.key().createdAt();
        assertThat(createdAt).isBetween(before.minusSeconds(1), after.plusSeconds(1));

        Long driftSeconds = jdbc.queryForObject(
                "select abs(extract(epoch from (now() - created_at)))::bigint from access.api_keys where api_key_id = ?",
                Long.class, issued.key().apiKeyId());
        assertThat(driftSeconds).as("stored created_at is the real moment, not shifted by a time zone offset")
                .isLessThan(120);
    }

    @Test
    @DisplayName("a full key lifecycle works on the hand-written tables")
    void lifecycleOnHandWrittenTables() {
        UUID owner = UUID.randomUUID();
        Permissions permissions = Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE);

        IssuedApiKeyResult issued = issueApiKey.handle(new IssueApiKeyCommand(owner, "sql", Lifetime.ONE_DAY, permissions));
        UUID id = issued.key().apiKeyId();
        assertThat(verifyApiKey.handle(new VerifyApiKeyQuery(issued.key().accessId(), issued.accessSecret())).permissions())
                .isEqualTo(permissions);

        updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "sql2", Lifetime.ONE_MONTH,
                new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ))));

        deleteApiKey.handle(new DeleteApiKeyCommand(owner, id));

        assertThat(jdbc.queryForList("select event_type from access.outbox order by change_version", String.class))
                .containsExactly("apiKey.projection.create", "apiKey.projection.update", "apiKey.projection.delete");
        assertThat(jdbc.queryForObject("select count(*) from access.api_keys", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from access.api_key_permissions", Long.class)).isZero();
    }
}
