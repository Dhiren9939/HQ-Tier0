package me.dhiren9939.backend.access.application.usecase.service.verify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A caller-supplied identifier is written to the log without line breaks or escapes, and never long. */
class LoggedIdentifierTest {

    @Test
    void keepsAWellFormedIdentifierAsItIs() {
        assertThat(LoggedIdentifier.of("V1_0PxK-z_9")).isEqualTo("V1_0PxK-z_9");
    }

    @Test
    void replacesEveryOtherCharacter() {
        assertThat(LoggedIdentifier.of("V1_a\r\nINFO forged\u001b[31m é"))
                .isEqualTo("V1_a??INFO?forged??31m??")
                .doesNotContain("\r", "\n", "\u001b", " ");
    }

    @Test
    void cutsALongIdentifierAndNotesItsLength() {
        String logged = LoggedIdentifier.of("a".repeat(1_000_000));

        assertThat(logged).isEqualTo("a".repeat(LoggedIdentifier.MAX_LOGGED_LENGTH) + "...(1000000 chars)");
    }

    @Test
    void anIdentifierAtTheCapIsNotCut() {
        String atCap = "a".repeat(LoggedIdentifier.MAX_LOGGED_LENGTH);

        assertThat(LoggedIdentifier.of(atCap)).isEqualTo(atCap);
    }

    @Test
    void aMissingIdentifierIsNamedAsMissing() {
        assertThat(LoggedIdentifier.of(null)).isEqualTo("<none>");
    }
}
