package me.dhiren9939.backend.integration;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.TimeZone;

/**
 * Base for tests that need a real PostgreSQL. A throwaway {@code postgres:17} container is started once
 * per JVM and shared by every subclass, so these tests never touch the dev database from
 * {@code docker-compose.yml}. Tagged {@code integration}: skip them with {@code -Dgroups='!integration'}.
 */
@Tag("integration")
@SpringBootTest
public abstract class PostgresIntegrationTest {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        // Same pin as BackendApplication.main: the JVM may report the deprecated alias "Asia/Calcutta",
        // which the Postgres server rejects when the JDBC driver sends it as the session time zone.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("access.outbox.relay.enabled", () -> "false"); // no broker here; rows stay in the outbox
    }
}
