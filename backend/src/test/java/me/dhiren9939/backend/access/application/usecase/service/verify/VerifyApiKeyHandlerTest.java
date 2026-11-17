package me.dhiren9939.backend.access.application.usecase.service.verify;

import me.dhiren9939.backend.access.application.interfaces.CredentialVerifier;
import me.dhiren9939.backend.common.access.VerificationFailureCode;
import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;
import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;
import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.PermissionChanges;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

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

    private static final String INVALID_MESSAGE = "The API key or secret is invalid.";
    private static final String MALFORMED_MESSAGE = "The API key is missing or not in a recognized format.";

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
    void anUpdateIsVisibleToVerificationAtOnce() {
        ApiKey key = apiKeys.existing(active());
        key.update(new me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyChanges(null, null,
                new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE))), NOW);

        VerifiedApiKey verified = verify("V1_acc", "secret_raw");

        assertThat(verified.permissions().levelOf(Scope.INGESTION)).isEqualTo(AccessLevel.WRITE);
    }

    @Test
    void anUnknownAccessIdAWrongSecretAndAnUnsupportedFormatAreOneOutcome() {
        apiKeys.existing(active());

        assertFailure(() -> verify("V1_unknown", "secret_raw"), VerificationFailureCode.INVALID_CREDENTIALS, INVALID_MESSAGE);
        assertFailure(() -> verify("V1_acc", "wrong"), VerificationFailureCode.INVALID_CREDENTIALS, INVALID_MESSAGE);
        assertFailure(() -> verify("V9_acc", "secret_raw"), VerificationFailureCode.INVALID_CREDENTIALS, INVALID_MESSAGE);
    }

    @Test
    void anExpiredKeyIsReportedAsExpiredToWhoeverHoldsItsSecret() {
        apiKeys.existing(active());
        VerifyApiKeyHandler later = handlerAt(Clock.fixed(NOW.plus(Duration.ofDays(40)), ZoneOffset.UTC));

        assertFailure(() -> later.handle(new VerifyApiKeyQuery("V1_acc", "secret_raw")),
                VerificationFailureCode.KEY_EXPIRED, "The API key has expired.");
    }

    @Test
    void aDeletedKeyIsReportedAsDeleted() {
        apiKeys.existing(deleted());

        assertFailure(() -> verify("V1_acc", "secret_raw"), VerificationFailureCode.KEY_DELETED, "The API key has been deleted.");
    }

    @Test
    @DisplayName("the state of a key is only revealed to someone with the right secret")
    void withAWrongSecretEveryKeyLooksTheSame() {
        for (ApiKey key : List.of(active(), deleted())) {
            InMemoryApiKeys fresh = new InMemoryApiKeys();
            fresh.existing(key);
            VerifyApiKeyHandler expired = new VerifyApiKeyHandler(fresh, List.of(V1), Clock.fixed(NOW.plus(Duration.ofDays(40)), ZoneOffset.UTC));

            assertFailure(() -> expired.handle(new VerifyApiKeyQuery("V1_acc", "wrong")),
                    VerificationFailureCode.INVALID_CREDENTIALS, INVALID_MESSAGE);
        }
    }

    @Test
    void malformedCredentialsHaveTheirOwnCode() {
        assertFailure(() -> verify("not-a-key", "secret_raw"), VerificationFailureCode.MALFORMED_CREDENTIALS, MALFORMED_MESSAGE);
        assertFailure(() -> verify(null, "secret_raw"), VerificationFailureCode.MALFORMED_CREDENTIALS, MALFORMED_MESSAGE);
        assertFailure(() -> verify("V1_acc", null), VerificationFailureCode.MALFORMED_CREDENTIALS, MALFORMED_MESSAGE);
    }

    @Test
    void presentedSecretsNeverAppearInToStrings() {
        assertThat(new VerifyApiKeyQuery("V1_acc", "topsecret").toString()).doesNotContain("topsecret");
        assertThat(ParsedApiKey.parse("V1_acc", "topsecret").toString()).doesNotContain("topsecret");
    }

    private static void assertFailure(Runnable action, VerificationFailureCode code, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiKeyVerificationFailure.class, failure -> assertThat(failure.code()).isEqualTo(code))
                .hasMessage(message);
    }
}
