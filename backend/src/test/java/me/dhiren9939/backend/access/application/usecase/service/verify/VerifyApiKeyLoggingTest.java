package me.dhiren9939.backend.access.application.usecase.service.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import me.dhiren9939.backend.access.application.interfaces.CredentialVerifier;
import me.dhiren9939.backend.access.application.usecase.InMemoryApiKeys;
import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static me.dhiren9939.backend.access.application.usecase.Fixtures.CLOCK;
import static me.dhiren9939.backend.access.application.usecase.Fixtures.active;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failed verifications are logged as one line with a fixed message and key-value fields; the caller's input only
 * appears sanitized and bounded, and an oversized secret is never hashed.
 */
class VerifyApiKeyLoggingTest {

    private final AtomicInteger secretChecks = new AtomicInteger();
    /**
     * Verifier for V1 that counts how often a secret reaches it (where the real one hashes it). Its shape: at most
     * 64 characters of {@code A-Z a-z 0-9 _ -} for both parts.
     */
    private final CredentialVerifier v1 = new CredentialVerifier() {
        @Override
        public boolean supports(String keyFormat) {
            return "V1".equals(keyFormat);
        }

        @Override
        public boolean isWellFormed(String accessId, String accessSecret) {
            return accessId.length() <= 64 && accessSecret.length() <= 64
                    && accessId.matches("[A-Za-z0-9_-]+") && accessSecret.matches("[A-Za-z0-9_-]+");
        }

        @Override
        public boolean matches(String presentedSecret, String storedSecretHash) {
            secretChecks.incrementAndGet();
            return ("hash-of-" + presentedSecret).equals(storedSecretHash);
        }
    };

    private final Logger handlerLog = (Logger) LoggerFactory.getLogger(VerifyApiKeyHandler.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private VerifyApiKeyHandler handler;

    @BeforeEach
    void setUp() {
        InMemoryApiKeys apiKeys = new InMemoryApiKeys();
        apiKeys.existing(active());
        handler = new VerifyApiKeyHandler(apiKeys, List.of(v1), CLOCK);
        logged.start();
        handlerLog.addAppender(logged);
    }

    @AfterEach
    void tearDown() {
        handlerLog.detachAppender(logged);
    }

    private void verifyFails(String accessId, String secret) {
        assertThatThrownBy(() -> handler.handle(new VerifyApiKeyQuery(accessId, secret)))
                .isInstanceOf(ApiKeyVerificationFailure.class);
    }

    private ILoggingEvent onlyEvent() {
        assertThat(logged.list).hasSize(1);
        return logged.list.getFirst();
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (KeyValuePair pair : event.getKeyValuePairs())
            fields.put(pair.key, pair.value);
        return fields;
    }

    @Test
    void aWrongSecretIsOneLineWithStructuredFields() {
        verifyFails("V1_acc", "wrong");

        ILoggingEvent event = onlyEvent();
        assertThat(event.getFormattedMessage()).isEqualTo(VerifyApiKeyHandler.FAILED_MESSAGE);
        assertThat(fields(event)).containsEntry("event", VerifyApiKeyHandler.FAILED_EVENT)
                .containsEntry("cause", "secret_mismatch")
                .containsEntry("accessId", "V1_acc")
                .containsEntry("keyFormat", "V1");
        assertThat(fields(event).get("reason")).hasToString("INVALID_CREDENTIALS");
    }

    @Test
    void anUnknownAccessIdIsLoggedWithItsCause() {
        verifyFails("V1_unknown", "secret_raw");

        assertThat(fields(onlyEvent())).containsEntry("cause", "unknown_access_id").containsEntry("accessId", "V1_unknown");
    }

    @Test
    void anAccessIdWithLineBreaksCannotForgeALogLine() {
        verifyFails("V1_acc\r\n2026-01-01 INFO admin logged in", "secret_raw");

        ILoggingEvent event = onlyEvent();
        assertThat(event.getFormattedMessage()).isEqualTo(VerifyApiKeyHandler.FAILED_MESSAGE);
        assertThat(fields(event)).containsEntry("cause", "malformed_credential")
                .containsEntry("accessId", "V1_acc??2026-01-01?INFO?admin?logged?in");
        assertThat(fields(event).values()).allSatisfy(value -> assertThat(String.valueOf(value)).doesNotContain("\n", "\r"));
    }

    @Test
    void anOversizedCredentialIsLoggedBoundedAndItsSecretIsNeverHashed() {
        String hugeId = "V1_" + "a".repeat(1_000_000);
        String hugeSecret = "s".repeat(1_000_000);

        verifyFails(hugeId, hugeSecret);

        ILoggingEvent event = onlyEvent();
        assertThat(String.valueOf(fields(event).get("accessId"))).hasSizeLessThan(100).endsWith("...(1000003 chars)");
        assertThat(fields(event)).containsEntry("accessIdLength", 1_000_003)
                .containsEntry("accessSecretLength", 1_000_000);
        assertThat(secretChecks).hasValue(0);
    }

    @Test
    void theSecretIsNeverLogged() {
        verifyFails("V1_acc", "wrong-but-secret");
        verifyFails("V1_acc\n", "malformed-but-secret");

        assertThat(logged.list).hasSize(2).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("secret");
            assertThat(fields(event).values()).noneSatisfy(value ->
                    assertThat(String.valueOf(value)).contains("but-secret"));
        });
    }

    @Test
    void aMissingQueryIsMalformedAndLogged() {
        assertThatThrownBy(() -> handler.handle(null)).isInstanceOf(ApiKeyVerificationFailure.class);

        assertThat(fields(onlyEvent())).containsEntry("cause", "malformed_credential").containsEntry("accessId", "<none>");
    }
}
