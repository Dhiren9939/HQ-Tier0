package me.dhiren9939.backend.access.domain.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A domain event the aggregate registers is complete and fits the key's columns; the message names the field. */
class ApiKeyEventRulesTest {

    private static final Instant T = Instant.parse("2026-01-15T11:00:00Z");
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static ApiKeyCreated created(String name, UUID id, long version, Instant occurredAt, UUID user,
                                         String accessId, String hash, Permissions permissions, Instant createdAt,
                                         Instant expiresAt) {
        return new ApiKeyCreated(name, id, version, occurredAt, user, accessId, hash, permissions, createdAt, expiresAt);
    }

    static Stream<Arguments> brokenEvents() {
        Permissions none = Permissions.none();
        String longText = "x".repeat(257);
        return Stream.of(
                Arguments.of("apiKeyName", (Runnable) () -> created(null, ID, 1, T, USER, "a", "h", none, T, null)),
                Arguments.of("apiKeyName", (Runnable) () -> created(" ", ID, 1, T, USER, "a", "h", none, T, null)),
                Arguments.of("apiKeyName", (Runnable) () -> created(longText, ID, 1, T, USER, "a", "h", none, T, null)),
                Arguments.of("apiKeyId", (Runnable) () -> created("n", null, 1, T, USER, "a", "h", none, T, null)),
                Arguments.of("version", (Runnable) () -> created("n", ID, 0, T, USER, "a", "h", none, T, null)),
                Arguments.of("occurredAt", (Runnable) () -> created("n", ID, 1, null, USER, "a", "h", none, T, null)),
                Arguments.of("occurredAt", (Runnable) () -> created("n", ID, 1, T.minusSeconds(1), USER, "a", "h", none, T, null)),
                Arguments.of("userId", (Runnable) () -> created("n", ID, 1, T, null, "a", "h", none, T, null)),
                Arguments.of("accessId", (Runnable) () -> created("n", ID, 1, T, USER, null, "h", none, T, null)),
                Arguments.of("accessId", (Runnable) () -> created("n", ID, 1, T, USER, longText, "h", none, T, null)),
                Arguments.of("accessSecretHash", (Runnable) () -> created("n", ID, 1, T, USER, "a", "", none, T, null)),
                Arguments.of("accessSecretHash", (Runnable) () -> created("n", ID, 1, T, USER, "a", longText, none, T, null)),
                Arguments.of("permissions", (Runnable) () -> created("n", ID, 1, T, USER, "a", "h", null, T, null)),
                Arguments.of("createdAt", (Runnable) () -> created("n", ID, 1, T, USER, "a", "h", none, null, null)),
                Arguments.of("expiresAt", (Runnable) () -> created("n", ID, 1, T, USER, "a", "h", none, T, T)),
                Arguments.of("expiresAt", (Runnable) () -> created("n", ID, 1, T, USER, "a", "h", none, T, T.minusSeconds(1))));
    }

    @ParameterizedTest(name = "{0} rejected")
    @MethodSource("brokenEvents")
    void aBrokenEventCannotBeCreated(String field, Runnable create) {
        assertThatThrownBy(create::run)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(field)
                .hasMessageContaining("apiKey.created");
    }

    @Test
    void everyEventTypeAppliesTheSameRules() {
        Permissions none = Permissions.none();

        assertThatThrownBy(() -> new ApiKeyUpdated("n", ID, 0, T, USER, "a", "h", none, T, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> new ApiKeyDeleted("n", ID, 0, T, USER, "a", "h", none, T, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }

    @Test
    void theEdgesOfTheRulesAreAllowed() {
        String max = "x".repeat(256);

        assertThat(created(max, ID, Long.MAX_VALUE, T, USER, max, max, Permissions.none(), T, null)).isNotNull();
        assertThat(created("n", ID, 1, T.plusSeconds(1), USER, "a", "h", Permissions.none(), T, T.plusNanos(1))).isNotNull();
    }
}
