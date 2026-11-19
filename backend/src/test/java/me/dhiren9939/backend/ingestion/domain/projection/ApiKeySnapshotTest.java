package me.dhiren9939.backend.ingestion.domain.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static me.dhiren9939.backend.ingestion.domain.projection.Snapshots.EXPIRES;
import static me.dhiren9939.backend.ingestion.domain.projection.Snapshots.KEY;
import static me.dhiren9939.backend.ingestion.domain.projection.Snapshots.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ingestion's own rules for a snapshot: nothing null, text non-blank and at most 256 characters, version at least 1. */
class ApiKeySnapshotTest {

    private static final KeyStatus ACTIVE = KeyStatus.ACTIVE;
    private static final IngestionAccess WRITE = IngestionAccess.WRITE;
    private static final Optional<Instant> EXP = Optional.of(EXPIRES);

    private static ApiKeySnapshot snapshot(UUID id, long version, KeyStatus status, UUID user, String accessId,
                                           String hash, IngestionAccess access, Optional<Instant> expiresAt) {
        return new ApiKeySnapshot(id, version, status, user, accessId, hash, access, expiresAt);
    }

    static Stream<Arguments> brokenSnapshots() {
        String longText = "x".repeat(257);
        return Stream.of(
                Arguments.of("apiKeyId", (Runnable) () -> snapshot(null, 1, ACTIVE, USER, "a", "h", WRITE, EXP)),
                Arguments.of("version", (Runnable) () -> snapshot(KEY, 0, ACTIVE, USER, "a", "h", WRITE, EXP)),
                Arguments.of("version", (Runnable) () -> snapshot(KEY, -1, ACTIVE, USER, "a", "h", WRITE, EXP)),
                Arguments.of("status", (Runnable) () -> snapshot(KEY, 1, null, USER, "a", "h", WRITE, EXP)),
                Arguments.of("userId", (Runnable) () -> snapshot(KEY, 1, ACTIVE, null, "a", "h", WRITE, EXP)),
                Arguments.of("accessId", (Runnable) () -> snapshot(KEY, 1, ACTIVE, USER, null, "h", WRITE, EXP)),
                Arguments.of("accessId", (Runnable) () -> snapshot(KEY, 1, ACTIVE, USER, " ", "h", WRITE, EXP)),
                Arguments.of("accessId", (Runnable) () -> snapshot(KEY, 1, ACTIVE, USER, longText, "h", WRITE, EXP)),
                Arguments.of("accessSecretHash", (Runnable) () -> snapshot(KEY, 1, ACTIVE, USER, "a", null, WRITE, EXP)),
                Arguments.of("accessSecretHash", (Runnable) () -> snapshot(KEY, 1, ACTIVE, USER, "a", "", WRITE, EXP)),
                Arguments.of("accessSecretHash", (Runnable) () -> snapshot(KEY, 1, ACTIVE, USER, "a", longText, WRITE, EXP)),
                Arguments.of("ingestionAccess", (Runnable) () -> snapshot(KEY, 1, ACTIVE, USER, "a", "h", null, EXP)),
                Arguments.of("expiresAt", (Runnable) () -> snapshot(KEY, 1, ACTIVE, USER, "a", "h", WRITE, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenSnapshots")
    void refusesAnInvalidSnapshotNamingTheField(String field, Runnable build) {
        assertThatThrownBy(build::run)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("API key snapshot: " + field + " ");
    }

    @Test
    void acceptsAValidSnapshot() {
        ApiKeySnapshot snapshot = snapshot(KEY, 1, ACTIVE, USER, "a".repeat(256), "h".repeat(256), WRITE, EXP);

        assertThat(snapshot.version()).isEqualTo(1);
        assertThat(snapshot.accessId()).hasSize(256);
    }

    @Test
    void anEmptyExpiresAtMeansTheKeyNeverExpires() {
        ApiKeySnapshot snapshot = snapshot(KEY, 1, ACTIVE, USER, "a", "h", IngestionAccess.NONE, Optional.empty());

        assertThat(snapshot.expiresAt()).isEmpty();
    }
}
