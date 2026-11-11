package me.dhiren9939.backend.access.application.usecase.user.issue;

import me.dhiren9939.backend.access.application.usecase.Fixtures;
import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.shared.access.events.outbound.CreateApiKeyProjectionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class IssueApiKeyHandlerTest {

    private InMemoryApiKeys apiKeys;
    private IssueApiKeyHandler handler;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
        handler = new IssueApiKeyHandler(apiKeys, () -> CREDENTIAL, CLOCK);
    }

    private IssuedApiKeyResult issue(Lifetime lifetime) {
        return handler.handle(new IssueApiKeyCommand(USER_ID, NAME, lifetime, NO_PERMISSIONS));
    }

    @Test
    void storesAProvisioningKeyAndReturnsTheSecretOnce() {
        IssuedApiKeyResult result = issue(Lifetime.ONE_MONTH);

        assertThat(result.accessSecret()).isEqualTo("secret_raw");
        assertThat(result.key().accessId()).isEqualTo("V1_acc");
        assertThat(result.key().state()).isEqualTo(ApiKeyState.PROVISIONING);
        assertThat(result.key().createdAt()).isEqualTo(NOW);
        assertThat(result.key().expiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);

        ApiKey stored = apiKeys.onlyKey();
        assertThat(stored.getAccessSecretHash()).isEqualTo("hash-of-secret_raw");
        assertThat(stored.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void registersExactlyOneCreatedEventAtRevisionOneCarryingTheHash() {
        issue(Lifetime.ONE_MONTH);

        assertThat(apiKeys.publishedEvents).hasSize(1);
        CreateApiKeyProjectionCommand event = (CreateApiKeyProjectionCommand) apiKeys.publishedEvents.getFirst();
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.emittedAt()).isNull();
        assertThat(event.accessSecretHash()).isEqualTo("hash-of-secret_raw");
    }

    @Test
    void neverLifetimeMeansNoExpiry() {
        assertThat(issue(Lifetime.NEVER).key().expiresAt()).isNull();
    }

    @Test
    void oneDayLifetimeExpiresTomorrow() {
        assertThat(issue(Lifetime.ONE_DAY).key().expiresAt()).isEqualTo(NOW.plusSeconds(24 * 3600));
    }

    @Test
    void theResultNeverPrintsTheSecret() {
        assertThat(issue(Lifetime.NEVER).toString()).doesNotContain("secret_raw");
    }
}
