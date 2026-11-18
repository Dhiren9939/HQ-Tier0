package me.dhiren9939.backend.integration;

import me.dhiren9939.backend.access.application.usecase.service.verify.ApiKeyVerificationFailure;
import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.common.access.VerificationFailureCode;
import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;
import me.dhiren9939.backend.access.application.usecase.service.verify.VerifiedApiKey;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssuedApiKeyResult;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.domain.aggregates.apikey.PermissionChanges;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The key lifecycle through the use cases, against a real PostgreSQL. HQ applies everything at once; the data plane conforms to the events. */
class ApiKeyLifecycleIntegrationTest extends AccessIntegrationTest {

    private final UUID owner = UUID.randomUUID();

    // ------------------------------------------------------------------------------------------
    // Issuing
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("issue: ACTIVE key readable by its owner, one apiKey.projection.create outbox row, secret never stored")
    void issue() throws Exception {
        IssuedApiKeyResult issued = issue(owner, "ci key", Lifetime.ONE_DAY, ingestionWrite());
        ApiKeyView key = issued.key();

        assertThat(issued.accessSecret()).isNotBlank();
        assertThat(key.state()).isEqualTo(ApiKeyState.ACTIVE);

        ApiKeyView read = view(owner, key.apiKeyId());
        assertThat(read.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(read.apiKeyName()).isEqualTo("ci key");
        assertThat(read.permissions()).isEqualTo(ingestionWrite());
        assertThat(read.expiresAt()).isNotNull();

        List<OutboxRow> rows = outbox();
        assertThat(rows).hasSize(1);
        OutboxRow created = rows.getFirst();
        assertThat(created.eventType()).isEqualTo("apiKey.projection.create");
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.apiKeyId()).isEqualTo(key.apiKeyId());
        assertThat(created.apiKeyName()).isEqualTo("ci key");
        assertThat(created.permissions()).isEqualTo("{\"INGESTION\":\"WRITE\"}");
        assertThat(created.occurredAt()).isNotNull();
        assertThat(created.emittedAt()).as("emitted_at is filled only by a relay").isNull();

        String expectedHash = sha256Hex(issued.accessSecret());
        assertThat(created.accessSecretHash()).isEqualTo(expectedHash);
        String storedHash = jdbc.queryForObject(
                "select access_secret_hash from access.api_keys where api_key_id = ?", String.class, key.apiKeyId());
        assertThat(storedHash).isEqualTo(expectedHash).isNotEqualTo(issued.accessSecret());
        assertThat(count("access.api_keys where access_secret_hash = '" + issued.accessSecret() + "'")).isZero();
        assertThat(count("access.outbox where access_secret_hash = '" + issued.accessSecret() + "'")).isZero();
        assertThat(count("access.api_key_permissions")).isEqualTo(1);
    }

    @Test
    @DisplayName("issue with no permissions stores no permission rows and a {} payload")
    void issueWithoutPermissions() {
        issue(owner, "bare", Lifetime.NEVER, Permissions.none());

        assertThat(outbox().getFirst().permissions()).isEqualTo("{}");
        assertThat(count("access.api_key_permissions")).isZero();
    }

    @Test
    @DisplayName("only the owner can read or change a key")
    void ownership() {
        ApiKeyView key = issueActive(owner, "mine", Lifetime.NEVER, ingestionWrite()).key();
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> view(stranger, key.apiKeyId())).isInstanceOf(ApiKeyNotFoundException.class);
        assertThatThrownBy(() -> updateApiKey.handle(
                new UpdateApiKeyCommand(stranger, key.apiKeyId(), "stolen", null, null)))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThatThrownBy(() -> deleteApiKey.handle(new DeleteApiKeyCommand(stranger, key.apiKeyId())))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThat(view(owner, key.apiKeyId()).apiKeyName()).isEqualTo("mine");
    }

    // ------------------------------------------------------------------------------------------
    // Verifying
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("verify: an ACTIVE key authenticates and returns identity and permissions")
    void verifySucceeds() {
        IssuedApiKeyResult issued = issueActive(owner, "k", Lifetime.ONE_MONTH, ingestionWrite());

        VerifiedApiKey verified = verifyApiKey.handle(
                new VerifyApiKeyQuery(issued.key().accessId(), issued.accessSecret()));

        assertThat(verified.apiKeyId()).isEqualTo(issued.key().apiKeyId());
        assertThat(verified.userId()).isEqualTo(owner);
        assertThat(verified.apiKeyName()).isEqualTo("k");
        assertThat(verified.permissions()).isEqualTo(ingestionWrite());
        assertThat(verified.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("verify: unknown ids and wrong secrets are one outcome; the key's state is reported only with the right secret")
    void verifyFailsWithTheRightCode() {
        IssuedApiKeyResult active = issueActive(owner, "active", Lifetime.ONE_DAY, ingestionWrite());

        assertVerifyFails("V1_doesNotExist", "whatever", VerificationFailureCode.INVALID_CREDENTIALS);
        assertVerifyFails(active.key().accessId(), "not-the-secret", VerificationFailureCode.INVALID_CREDENTIALS);

        // still fine now, but past its one-day lifetime it must stop authenticating
        assertThat(verifyApiKey.handle(new VerifyApiKeyQuery(active.key().accessId(), active.accessSecret())))
                .isNotNull();
        clock.pinTo(Instant.now().plus(Duration.ofDays(2)));
        assertVerifyFails(active.key().accessId(), active.accessSecret(), VerificationFailureCode.KEY_EXPIRED);
    }

    // ------------------------------------------------------------------------------------------
    // Updating
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("update: applies at once and writes apiKey.projection.update at version 2")
    void update() {
        IssuedApiKeyResult issued = issueActive(owner, "old", Lifetime.ONE_DAY, ingestionWrite());
        UUID id = issued.key().apiKeyId();
        Instant oldExpiry = view(owner, id).expiresAt();

        ApiKeyView updated = updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "renamed", Lifetime.ONE_MONTH,
                new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ))));

        assertThat(updated.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(updated.apiKeyName()).isEqualTo("renamed");
        assertThat(updated.permissions()).isEqualTo(ingestionWrite().with(Scope.TENANT, AccessLevel.READ));

        List<OutboxRow> rows = outbox();
        assertThat(rows).hasSize(2);
        OutboxRow updatedRow = rows.get(1);
        assertThat(updatedRow.eventType()).isEqualTo("apiKey.projection.update");
        assertThat(updatedRow.version()).isEqualTo(2);
        assertThat(updatedRow.apiKeyName()).isEqualTo("renamed");
        assertThat(updatedRow.permissions()).isEqualTo("{\"INGESTION\":\"WRITE\",\"TENANT\":\"READ\"}");
        assertThat(updatedRow.accessSecretHash()).as("every event is a full snapshot, hash included")
                .isNotNull().isEqualTo(rows.get(0).accessSecretHash());
        assertThat(updatedRow.emittedAt()).isNull();

        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class)).isEqualTo(2);
        assertThat(count("access.api_key_permissions")).isEqualTo(2);

        // verification sees the new state at once
        VerifiedApiKey verified = verifyApiKey.handle(new VerifyApiKeyQuery(issued.key().accessId(), issued.accessSecret()));
        assertThat(verified.apiKeyName()).isEqualTo("renamed");
        assertThat(verified.permissions()).isEqualTo(ingestionWrite().with(Scope.TENANT, AccessLevel.READ));
        assertThat(verified.expiresAt()).isAfter(oldExpiry.plus(Duration.ofDays(20)));
    }

    @Test
    @DisplayName("an update that changes nothing writes no event and no revision")
    void noOpUpdate() {
        IssuedApiKeyResult issued = issueActive(owner, "same", Lifetime.NEVER, ingestionWrite());
        UUID id = issued.key().apiKeyId();
        int before = outbox().size();

        ApiKeyView result = updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "same", Lifetime.NEVER,
                new PermissionChanges(Map.of(Scope.INGESTION, AccessLevel.WRITE, Scope.TENANT, AccessLevel.NONE))));

        assertThat(result.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(outbox()).hasSize(before);
        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("a deleted key is gone: read, update and delete all report not found")
    void deletedKeyIsGone() {
        UUID id = issueActive(owner, "k", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();
        deleteApiKey.handle(new DeleteApiKeyCommand(owner, id));

        assertThatThrownBy(() -> view(owner, id)).isInstanceOf(ApiKeyNotFoundException.class);
        assertThatThrownBy(() -> updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "x", null, null)))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThatThrownBy(() -> deleteApiKey.handle(new DeleteApiKeyCommand(owner, id)))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThat(outbox()).hasSize(2);
    }

    // ------------------------------------------------------------------------------------------
    // Deleting
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("delete removes the row at once and writes apiKey.projection.delete at version 2")
    void deleteRemovesTheRow() {
        IssuedApiKeyResult issued = issueActive(owner, "doomed", Lifetime.NEVER, ingestionWrite());
        UUID id = issued.key().apiKeyId();
        String accessId = issued.key().accessId();

        deleteApiKey.handle(new DeleteApiKeyCommand(owner, id));

        List<OutboxRow> rows = outbox();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).eventType()).isEqualTo("apiKey.projection.delete");
        assertThat(rows.get(1).version()).isEqualTo(2);
        assertThat(rows.get(1).permissions()).isEqualTo("{\"INGESTION\":\"WRITE\"}");
        assertThat(rows.get(1).accessSecretHash()).as("the delete event is a full snapshot, hash included")
                .isNotNull().isEqualTo(rows.get(0).accessSecretHash());
        assertThat(count("access.api_keys")).isZero();
        assertThat(count("access.api_key_permissions")).isZero();
        assertThat(count("access.api_keys where access_id = '" + accessId + "'"))
                .as("the Access ID is free again at once").isZero();
        assertThatThrownBy(() -> view(owner, id)).isInstanceOf(ApiKeyNotFoundException.class);
        assertVerifyFails(accessId, issued.accessSecret(), VerificationFailureCode.INVALID_CREDENTIALS);
    }

    // ------------------------------------------------------------------------------------------

    private void assertVerifyFails(String accessId, String secret, VerificationFailureCode code) {
        assertThatThrownBy(() -> verifyApiKey.handle(new VerifyApiKeyQuery(accessId, secret)))
                .isInstanceOfSatisfying(ApiKeyVerificationFailure.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static String sha256Hex(String secret) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8)));
    }
}
