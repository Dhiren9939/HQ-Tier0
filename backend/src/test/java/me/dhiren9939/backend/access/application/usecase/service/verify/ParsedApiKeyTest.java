package me.dhiren9939.backend.access.application.usecase.service.verify;

import me.dhiren9939.backend.common.access.VerificationFailureCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only the key format prefix is read here; what a valid key of that format looks like is the format's rule. */
class ParsedApiKeyTest {

    private static void assertMalformed(String accessId, String secret) {
        assertThatThrownBy(() -> ParsedApiKey.parse(accessId, secret))
                .isInstanceOfSatisfying(ApiKeyVerificationFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(VerificationFailureCode.MALFORMED_CREDENTIALS));
    }

    @Test
    void readsTheKeyFormatFromTheAccessIdPrefix() {
        ParsedApiKey parsed = ParsedApiKey.parse("V1_0PxK-z_9", "secret");

        assertThat(parsed.keyFormat()).isEqualTo("V1");
        assertThat(parsed.accessId()).isEqualTo("V1_0PxK-z_9");
        assertThat(parsed.accessSecret()).isEqualTo("secret");
    }

    @Test
    void theKeyFormatHasOneToThreeDigits() {
        assertThat(ParsedApiKey.parse("V123_x", "s").keyFormat()).isEqualTo("V123");
        assertMalformed("V1234_x", "s");
    }

    @Test
    void leavesTheRestToTheKeyFormatWhateverItsLength() {
        ParsedApiKey parsed = ParsedApiKey.parse("V1_" + "a".repeat(1_000_000), "s".repeat(1_000_000));

        assertThat(parsed.keyFormat()).isEqualTo("V1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "V1", "V1acc", "V_acc", "v1_acc", " V1_acc", "\nV1_acc", "not-a-key"})
    void refusesAnAccessIdWithoutAFormatPrefix(String accessId) {
        assertMalformed(accessId, "s");
    }

    @Test
    void refusesMissingParts() {
        assertMalformed(null, "s");
        assertMalformed("V1_acc", null);
    }

    @Test
    void neverPrintsTheSecretAndPrintsTheIdSanitized() {
        String text = ParsedApiKey.parse("V1_acc\r\nforged", "topsecret").toString();

        assertThat(text).doesNotContain("topsecret", "\r", "\n").contains("V1_acc??forged");
    }
}
