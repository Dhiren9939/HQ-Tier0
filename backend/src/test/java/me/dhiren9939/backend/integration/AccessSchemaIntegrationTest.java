package me.dhiren9939.backend.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The schema Hibernate creates with the app's real configuration (ddl-auto=update). */
class AccessSchemaIntegrationTest extends AccessIntegrationTest {

    private static final Map<String, List<String>> EXPECTED_COLUMNS = Map.of(
            "api_keys", List.of("api_key_id", "lock_version", "user_id", "access_id", "access_secret_hash",
                    "api_key_name", "created_at", "expires_at", "deleted_at", "pending_change_id",
                    "revision", "confirmed_revision"),
            "pending_changes", List.of("change_id", "lock_version", "revision", "requested_at", "kind", "status",
                    "failure_reason", "api_key_name", "expires_at"),
            "api_key_permissions", List.of("api_key_id", "scope", "access_level"),
            "pending_change_permissions", List.of("change_id", "scope", "access_level"),
            "outbox", List.of("change_kind", "failure_reason", "event_id", "event_type", "api_key_id", "change_version", "occurred_at", "emitted_at",
                    "user_id", "access_id", "access_secret_hash", "api_key_name", "permissions", "created_at"));

    @Test
    @DisplayName("schema `access` exists and nothing was created in `public`")
    void schemaExists() {
        List<String> schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata where schema_name = 'access'", String.class);
        assertThat(schemas).containsExactly("access");

        List<String> publicTables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(publicTables).isEmpty();
    }

    @Test
    @DisplayName("every table exists with exactly the expected columns")
    void tablesAndColumns() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'access'", String.class);
        assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_COLUMNS.keySet());

        EXPECTED_COLUMNS.forEach((table, columns) -> {
            List<String> actual = jdbc.queryForList("""
                    select column_name from information_schema.columns
                    where table_schema = 'access' and table_name = ?
                    """, String.class, table);
            assertThat(actual).as("columns of access.%s", table).containsExactlyInAnyOrderElementsOf(columns);
        });
    }

    @Test
    @DisplayName("per-scope permission columns and the event expiry column are gone")
    void removedColumnsAreGone() {
        List<String> removed = jdbc.queryForList("""
                select table_name || '.' || column_name from information_schema.columns
                where table_schema = 'access'
                  and (column_name in ('ingestion_permission', 'tenant_permission', 'channel_permission',
                                       'applied_version', 'issued_version', 'desired_version')
                       or (table_name = 'outbox' and column_name = 'expires_at'))
                """, String.class);
        assertThat(removed).isEmpty();
    }

    @Test
    @DisplayName("the unique and foreign key constraints exist")
    void constraints() {
        List<String> uniqueColumns = jdbc.queryForList("""
                select kcu.table_name || '.' || kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name and kcu.table_schema = tc.table_schema
                where tc.table_schema = 'access' and tc.constraint_type = 'UNIQUE'
                """, String.class);
        assertThat(uniqueColumns).contains("api_keys.access_id", "api_keys.pending_change_id");

        Long foreignKeys = jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_schema = 'access' and constraint_type = 'FOREIGN KEY'
                """, Long.class);
        assertThat(foreignKeys).isEqualTo(3);
    }
}
