package me.dhiren9939.backend.access.application.usecase.service.verify;

import me.dhiren9939.backend.access.application.usecase.Fixtures;
import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;

import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Scope;
import java.util.Map;
import me.dhiren9939.backend.access.application.interfaces.CredentialVerifier;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.PermissionChanges;
import me.dhiren9939.backend.shared.access.ApiKeyParsingFailure;
import me.dhiren9939.backend.shared.access.ApiKeyVerificationFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifyApiKeyHandlerTest {

    /** Verifier for format V1 whose "hash" of a secret is {@code "hash-of-" + secret}. */
    private static final CredentialVerifier V1 = new CredentialVerifier() {
        @Override
        public boolean supports(String keyFormat) {
            return "V1".equals(keyFormat);
        }

        @Override
        public boolean matches(String presentedSecret, String storedSecretHash) {
            return ("hash-of-" + presentedSecret).equals(storedSecretHash);
        }
    };

    private InMemoryApiKeys apiKeys;
    private VerifyApiKeyHandler handler;

    @BeforeEach
    void setUp() {
        apiKeys = new InMemoryApiKeys();
        handler = handlerAt(CLOCK);
    }

    private VerifyApiKeyHandler handlerAt(Clock clock) {
        return new VerifyApiKeyHandler(apiKeys, List.of(V1), clock);
    }

    private VerifiedApiKey verify(String accessId, String secret) {
        return handler.handle(new VerifyApiKeyQuery(accessId, secret));
    }

    @Test
    void anActiveKeyWithTheRightSecretAuthenticates() {
        ApiKey key = apiKeys.existing(active());

        VerifiedApiKey verified = verify("V1_acc", "secret_raw");

        assertThat(verified.apiKeyId()).isEqualTo(key.getApiKeyId());
        assertThat(verified.userId()).isEqualTo(USER_ID);
        assertThat(verified.apiKeyName()).isEqualTo(NAME);
        assertThat(verified.permissions()).isEqualTo(NO_PERMISSIONS);
        assertThat(verified.createdAt()).isEqualTo(NOW);
        assertThat(verified.expiresAt()).isEqualTo(ONE_MONTH_FROM_NOW);
    }

    @Test
    void aKeyWithAPendingUpdateStillAuthenticatesWithItsConfirmedPermissions() {
        ApiKey key = apiKeys.existing(active());
        key.requestUpdate(new me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyChanges(null, null,
                new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE))), NOW);

        VerifiedApiKey verified = verify("V1_acc", "secret_raw");

        assertThat(verified.permissions()).isEqualTo(NO_PERMISSIONS);
    }

    @Test
    void aKeyWhoseDeletionIsStillPendingAuthenticates() {
        apiKeys.existing(changePending());

        assertThat(verify("V1_acc", "secret_raw")).isNotNull();
    }

    @Test
    void everyNonAuthenticatableCaseFailsWithTheSameMessage() {
        apiKeys.existing(active());

        // wrong secret
        assertFailure(() -> verify("V1_acc", "wrong"));
        // unknown access id
        assertFailure(() -> verify("V1_unknown", "secret_raw"));
        // key format nobody can verify
        assertFailure(() -> verify("V9_acc", "secret_raw"));
        // expired
        VerifyApiKeyHandler later = handlerAt(Clock.fixed(NOW.plus(Duration.ofDays(40)), ZoneOffset.UTC));
        assertFailure(() -> later.handle(new VerifyApiKeyQuery("V1_acc", "secret_raw")));
    }

    @Test
    void provisioningFailedAndDeletedKeysFailLikeUnknownOnes() {
        for (ApiKey key : List.of(provisioning(), provisioningFailed(), deleted())) {
            InMemoryApiKeys fresh = new InMemoryApiKeys();
            fresh.existing(key);
            VerifyApiKeyHandler h = new VerifyApiKeyHandler(fresh, List.of(V1), CLOCK);

            assertFailure(() -> h.handle(new VerifyApiKeyQuery("V1_acc", "secret_raw")));
        }
    }

    @Test
    void malformedAccessIdsAreAParsingFailureNotAnAuthenticationOutcome() {
        assertThatThrownBy(() -> verify("not-a-key", "secret_raw")).isInstanceOf(ApiKeyParsingFailure.class);
        assertThatThrownBy(() -> verify(null, "secret_raw")).isInstanceOf(ApiKeyParsingFailure.class);
        assertThatThrownBy(() -> verify("V1_acc", null)).isInstanceOf(ApiKeyParsingFailure.class);
    }

    @Test
    void presentedSecretsNeverAppearInToStrings() {
        assertThat(new VerifyApiKeyQuery("V1_acc", "topsecret").toString()).doesNotContain("topsecret");
        assertThat(ParsedApiKey.parse("V1_acc", "topsecret").toString()).doesNotContain("topsecret");
    }

    private static void assertFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiKeyVerificationFailure.class)
                .hasMessage(VerifyApiKeyHandler.FAILURE_MESSAGE);
    }
}
