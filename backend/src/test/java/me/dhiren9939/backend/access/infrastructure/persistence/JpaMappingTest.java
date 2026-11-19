package me.dhiren9939.backend.access.infrastructure.persistence;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.infrastructure.persistence.models.EventOutboxEntity;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the JPA mappings without a database: Hibernate builds its boot metadata offline, which
 * validates the annotations and shows the tables and columns it would use. This does not prove
 * behaviour against a real Postgres.
 */
class JpaMappingTest {

    private static StandardServiceRegistry registry;
    private static Metadata metadata;

    @BeforeAll
    static void buildMetadata() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .build();
        metadata = new MetadataSources(registry)
                .addAnnotatedClass(ApiKey.class)
                .addAnnotatedClass(EventOutboxEntity.class)
                .buildMetadata();
    }

    @AfterAll
    static void close() {
        StandardServiceRegistryBuilder.destroy(registry);
    }

    private static List<String> columns(Table table) {
        return table.getColumns().stream().map(Column::getName).map(String::toLowerCase).toList();
    }

    private static Table entityTable(Class<?> type) {
        return metadata.getEntityBinding(type.getName()).getTable();
    }

    @Test
    void apiKeyPermissionsAreScopeToLevelRowsInTheAccessSchema() {
        Table table = metadata.getCollectionBinding(ApiKey.class.getName() + ".permissionLevels").getCollectionTable();

        assertThat(table.getSchema()).isEqualToIgnoringCase("access");
        assertThat(table.getName()).isEqualTo("api_key_permissions");
        assertThat(columns(table)).containsExactlyInAnyOrder("api_key_id", "scope", "access_level");
    }

    @Test
    void thereAreNoPerScopeColumnsLeftOnTheEntityTables() {
        assertThat(columns(entityTable(ApiKey.class)))
                .doesNotContain("ingestion_permission", "tenant_permission", "channel_permission");
    }

    @Test
    void theOutboxKeepsOnlyMetadataColumnsAndOneJsonbPayload() {
        assertThat(columns(entityTable(EventOutboxEntity.class))).containsExactlyInAnyOrder("event_id", "event_type",
                "api_key_id", "change_version", "occurred_at", "emitted_at", "payload", "attempts", "last_error");
    }

    @Test
    void everyEntityTableLivesInTheAccessSchema() {
        for (Class<?> type : List.of(ApiKey.class, EventOutboxEntity.class))
            assertThat(entityTable(type).getSchema()).isEqualToIgnoringCase("access");
    }
}
