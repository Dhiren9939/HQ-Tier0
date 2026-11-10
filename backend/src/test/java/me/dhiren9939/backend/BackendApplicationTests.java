package me.dhiren9939.backend;

import me.dhiren9939.backend.integration.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

/** Boots the whole application against a throwaway PostgreSQL (see {@link PostgresIntegrationTest}). */
class BackendApplicationTests extends PostgresIntegrationTest {

	@Test
	void contextLoads() {
	}

}
