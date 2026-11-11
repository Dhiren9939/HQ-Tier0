package me.dhiren9939.backend.integration;

import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeConfirmed;
import me.dhiren9939.backend.access.application.usecase.delete.DeleteApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.cancel.CancelApiKeyChangeCommand;
import me.dhiren9939.backend.access.application.usecase.dismiss.DismissApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.issue.IssuedApiKeyResult;
import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeRejected;
import me.dhiren9939.backend.access.application.usecase.update.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.verify.VerifiedApiKey;
import me.dhiren9939.backend.access.application.usecase.verify.VerifyApiKeyQuery;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ChangeStatus;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Lifetime;
import me.dhiren9939.backend.access.domain.aggregates.apikey.PermissionChanges;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.ApiKeyVerificationFailure;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.Scope;
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

/** The key lifecycle through the input ports, against a real PostgreSQL. */
class ApiKeyLifecycleIntegrationTest extends AccessIntegrationTest {

    private final UUID owner = UUID.randomUUID();

    // ------------------------------------------------------------------------------------------
    // Issuing
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("issue: PROVISIONING key readable by its owner, one apiKey.projection.create outbox row, secret never stored")
    void issue() throws Exception {
        IssuedApiKeyResult issued = issue(owner, "ci key", Lifetime.ONE_DAY, ingestionWrite());
        ApiKeyView key = issued.key();

        assertThat(issued.accessSecret()).isNotBlank();
        assertThat(key.state()).isEqualTo(ApiKeyState.PROVISIONING);
        assertThat(key.updateStatus()).isEqualTo(ChangeStatus.PENDING);

        ApiKeyView read = view(owner, key.apiKeyId());
        assertThat(read.state()).isEqualTo(ApiKeyState.PROVISIONING);
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

        assertThat(count("access.pending_changes")).isEqualTo(1);
        assertThat(count("access.pending_change_permissions")).isEqualTo(1);
        // issuing records the requested attributes on the key itself; it only cannot authenticate yet
        assertThat(count("access.api_key_permissions")).isEqualTo(1);
    }

    @Test
    @DisplayName("issue with no permissions stores no permission rows and a {} payload")
    void issueWithoutPermissions() {
        issue(owner, "bare", Lifetime.NEVER, Permissions.none());

        assertThat(outbox().getFirst().permissions()).isEqualTo("{}");
        assertThat(count("access.pending_change_permissions")).isZero();
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
        assertThatThrownBy(() -> dismissApiKey.handle(new DismissApiKeyCommand(stranger, key.apiKeyId())))
                .isInstanceOf(ApiKeyNotFoundException.class);
        assertThat(view(owner, key.apiKeyId()).apiKeyName()).isEqualTo("mine");
    }

    // ------------------------------------------------------------------------------------------
    // Confirming and verifying
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("confirm(1): key becomes ACTIVE, its permissions are stored, the pending change is gone")
    void confirmCreation() {
        IssuedApiKeyResult issued = issue(owner, "k", Lifetime.NEVER,
                ingestionWrite().with(Scope.TENANT, AccessLevel.READ));

        confirmChange.handle(new ApiKeyChangeConfirmed(issued.key().apiKeyId(), 1));

        ApiKeyView key = view(owner, issued.key().apiKeyId());
        assertThat(key.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(key.updateStatus()).isNull();
        assertThat(key.permissions()).isEqualTo(ingestionWrite().with(Scope.TENANT, AccessLevel.READ));
        assertThat(count("access.pending_changes")).isZero();
        assertThat(count("access.pending_change_permissions")).isZero();
        assertThat(count("access.api_key_permissions")).isEqualTo(2);
        assertThat(outbox()).as("confirming writes no event").hasSize(1);
    }

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
    @DisplayName("verify: unknown, still-provisioning, wrong-secret and expired keys fail identically")
    void verifyFailsIdentically() {
        IssuedApiKeyResult provisioning = issue(owner, "provisioning", Lifetime.NEVER, ingestionWrite());
        IssuedApiKeyResult active = issueActive(owner, "active", Lifetime.ONE_DAY, ingestionWrite());

        assertVerifyFails(provisioning.key().accessId(), provisioning.accessSecret());
        assertVerifyFails("V1_doesNotExist", "whatever");
        assertVerifyFails(active.key().accessId(), "not-the-secret");

        // still fine now, but past its one-day lifetime it must stop authenticating
        assertThat(verifyApiKey.handle(new VerifyApiKeyQuery(active.key().accessId(), active.accessSecret())))
                .isNotNull();
        clock.pinTo(Instant.now().plus(Duration.ofDays(2)));
        assertVerifyFails(active.key().accessId(), active.accessSecret());
    }

    // ------------------------------------------------------------------------------------------
    // Updating
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("update: pending UPDATE + apiKey.projection.update at revision 2; the confirmed state is unchanged until confirm")
    void update() {
        IssuedApiKeyResult issued = issueActive(owner, "old", Lifetime.ONE_DAY, ingestionWrite());
        UUID id = issued.key().apiKeyId();

        ApiKeyView pending = updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "renamed", Lifetime.ONE_MONTH,
                new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ))));

        assertThat(pending.state()).isEqualTo(ApiKeyState.CHANGE_PENDING);
        assertThat(pending.updateStatus()).isEqualTo(ChangeStatus.PENDING);
        assertThat(pending.apiKeyName()).as("confirmed name until confirm").isEqualTo("old");
        assertThat(pending.permissions()).isEqualTo(ingestionWrite());

        List<OutboxRow> rows = outbox();
        assertThat(rows).hasSize(2);
        OutboxRow updated = rows.get(1);
        assertThat(updated.eventType()).isEqualTo("apiKey.projection.update");
        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.apiKeyName()).isEqualTo("renamed");
        assertThat(updated.permissions()).isEqualTo("{\"INGESTION\":\"WRITE\",\"TENANT\":\"READ\"}");
        assertThat(updated.accessSecretHash()).as("only apiKey.projection.create carries the hash").isNull();
        assertThat(updated.emittedAt()).isNull();

        assertThat(count("access.pending_changes")).isEqualTo(1);
        assertThat(count("access.pending_change_permissions")).isEqualTo(2);
        assertThat(count("access.api_key_permissions")).isEqualTo(1);

        // the old permissions still authenticate until the data plane confirms
        VerifiedApiKey stillOld = verifyApiKey.handle(new VerifyApiKeyQuery(issued.key().accessId(), issued.accessSecret()));
        assertThat(stillOld.permissions()).isEqualTo(ingestionWrite());
        assertThat(stillOld.apiKeyName()).isEqualTo("old");
    }

    @Test
    @DisplayName("confirm(2) applies name, permissions and expiry, and clears every pending row")
    void confirmUpdate() {
        IssuedApiKeyResult issued = issueActive(owner, "old", Lifetime.ONE_DAY, ingestionWrite());
        UUID id = issued.key().apiKeyId();
        Instant oldExpiry = view(owner, id).expiresAt();
        updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "renamed", Lifetime.ONE_MONTH,
                new PermissionChanges(Map.of(Scope.TENANT, AccessLevel.READ_WRITE, Scope.INGESTION, AccessLevel.NONE))));

        confirmChange.handle(new ApiKeyChangeConfirmed(id, 2));

        ApiKeyView key = view(owner, id);
        assertThat(key.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(key.apiKeyName()).isEqualTo("renamed");
        assertThat(key.permissions()).isEqualTo(Permissions.none().with(Scope.TENANT, AccessLevel.READ_WRITE));
        assertThat(key.expiresAt()).isAfter(oldExpiry.plus(Duration.ofDays(20)));
        assertThat(count("access.pending_changes")).isZero();
        assertThat(count("access.pending_change_permissions")).isZero();
        assertThat(count("access.api_key_permissions")).as("INGESTION=NONE leaves no row").isEqualTo(1);
        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select confirmed_revision from access.api_keys", Long.class)).isEqualTo(2);
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
        assertThat(count("access.pending_changes")).isZero();
        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("update or delete while a change is pending is refused; stale revisions cannot be confirmed")
    void pendingChangeBlocksEverything() {
        UUID id = issueActive(owner, "k", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();
        updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "first", null, null));
        int events = outbox().size();

        assertThatThrownBy(() -> updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "second", null, null)))
                .isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> deleteApiKey.handle(new DeleteApiKeyCommand(owner, id)))
                .isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> confirmChange.handle(new ApiKeyChangeConfirmed(id, 1)))
                .as("revision 1 is no longer the pending one").isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> confirmChange.handle(new ApiKeyChangeConfirmed(id, 3)))
                .isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> rejectChange.handle(new ApiKeyChangeRejected(id, 1, FailureReason.REJECTED)))
                .isInstanceOf(ApiKeyUpdateFailure.class);

        assertThat(outbox()).hasSize(events);
        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.CHANGE_PENDING);
        assertThat(jdbc.queryForObject("select revision from access.api_keys", Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("a provisioning key cannot be updated or deleted")
    void provisioningKeyCannotChange() {
        UUID id = issue(owner, "k", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();

        assertThatThrownBy(() -> updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "x", null, null)))
                .isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> deleteApiKey.handle(new DeleteApiKeyCommand(owner, id)))
                .isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(outbox()).hasSize(1);
    }

    @Test
    @DisplayName("reject -> CHANGE_FAILED with its reason stored and an update restating the confirmed state; dismiss -> ACTIVE with no event and no leftovers")
    void rejectedUpdateThenDismiss() {
        UUID id = issueActive(owner, "keep", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();
        updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "lost", null, null));
        int events = outbox().size();

        rejectChange.handle(new ApiKeyChangeRejected(id, 2, FailureReason.CONFIRM_TIMEOUT));

        List<OutboxRow> afterReject = outbox();
        assertThat(afterReject).hasSize(events + 1);
        OutboxRow compensation = afterReject.getLast();
        assertThat(compensation.eventType()).isEqualTo("apiKey.projection.update");
        assertThat(compensation.version()).isEqualTo(3);
        assertThat(compensation.apiKeyName()).as("restates the confirmed name").isEqualTo("keep");
        ApiKeyView failed = view(owner, id);
        assertThat(failed.state()).isEqualTo(ApiKeyState.CHANGE_FAILED);
        assertThat(failed.updateStatus()).isEqualTo(ChangeStatus.FAILED);
        assertThat(jdbc.queryForObject("select failure_reason from access.pending_changes", String.class))
                .isEqualTo("CONFIRM_TIMEOUT");
        assertThatThrownBy(() -> updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "again", null, null)))
                .as("a failed change must be dismissed first").isInstanceOf(ApiKeyUpdateFailure.class);

        dismissApiKey.handle(new DismissApiKeyCommand(owner, id));

        ApiKeyView active = view(owner, id);
        assertThat(active.state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(active.apiKeyName()).isEqualTo("keep");
        assertThat(outbox()).as("dismissing emits nothing").hasSize(events + 1);
        assertThat(count("access.pending_changes")).isZero();
        assertThat(count("access.pending_change_permissions")).isZero();
        assertThat(count("access.api_key_permissions")).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // Deleting
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("delete -> confirm leaves a DELETED tombstone; dismiss removes everything with no new event")
    void deleteTombstone() {
        IssuedApiKeyResult issued = issueActive(owner, "doomed", Lifetime.NEVER, ingestionWrite());
        UUID id = issued.key().apiKeyId();
        String accessId = issued.key().accessId();

        deleteApiKey.handle(new DeleteApiKeyCommand(owner, id));

        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.CHANGE_PENDING);
        List<OutboxRow> rows = outbox();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).eventType()).isEqualTo("apiKey.projection.delete");
        assertThat(rows.get(1).version()).isEqualTo(2);
        assertThat(rows.get(1).permissions()).isEqualTo("{\"INGESTION\":\"WRITE\"}");
        assertThat(verifyApiKey.handle(new VerifyApiKeyQuery(accessId, issued.accessSecret())))
                .as("a pending delete still authenticates until confirmed").isNotNull();

        confirmChange.handle(new ApiKeyChangeConfirmed(id, 2));

        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.DELETED);
        assertThat(jdbc.queryForObject("select deleted_at is not null from access.api_keys", Boolean.class)).isTrue();
        assertVerifyFails(accessId, issued.accessSecret());
        assertThatThrownBy(() -> updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "x", null, null)))
                .isInstanceOf(ApiKeyUpdateFailure.class);
        assertThatThrownBy(() -> deleteApiKey.handle(new DeleteApiKeyCommand(owner, id)))
                .isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(count("access.api_keys where access_id = '" + accessId + "'"))
                .as("the Access ID stays reserved while the tombstone exists").isEqualTo(1);

        dismissApiKey.handle(new DismissApiKeyCommand(owner, id));

        assertThat(outbox()).as("dismissing a tombstone emits nothing").hasSize(2);
        assertThat(count("access.api_keys")).isZero();
        assertThat(count("access.api_key_permissions")).isZero();
        assertThat(count("access.pending_changes")).isZero();
        assertThat(count("access.pending_change_permissions")).isZero();
        assertThatThrownBy(() -> view(owner, id)).isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test
    @DisplayName("a rejected delete -> CHANGE_FAILED; dismiss brings the key back ACTIVE")
    void rejectedDelete() {
        UUID id = issueActive(owner, "survivor", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();
        deleteApiKey.handle(new DeleteApiKeyCommand(owner, id));

        rejectChange.handle(new ApiKeyChangeRejected(id, 2, FailureReason.REJECTED));
        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.CHANGE_FAILED);

        List<OutboxRow> afterReject = outbox();
        assertThat(afterReject).extracting(OutboxRow::eventType)
                .containsExactly("apiKey.projection.create", "apiKey.projection.delete", "apiKey.projection.create");
        assertThat(afterReject.getLast().version()).isEqualTo(3);
        assertThat(afterReject.getLast().accessSecretHash()).as("the restoring create carries the hash").isNotNull();

        dismissApiKey.handle(new DismissApiKeyCommand(owner, id));
        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(outbox()).as("dismissing emits nothing").hasSize(3);
        assertThat(count("access.pending_changes")).isZero();
    }

    @Test
    @DisplayName("the owner cancels a pending update: CANCELLED_BY_USER, an update restating the confirmed state goes out at once, dismiss emits nothing")
    void cancelledUpdate() {
        UUID id = issueActive(owner, "keep", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();
        updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "changed-mind", null, null));
        int events = outbox().size();

        cancelApiKeyChange.handle(new CancelApiKeyChangeCommand(owner, id));

        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.CHANGE_FAILED);
        assertThat(jdbc.queryForObject("select failure_reason from access.pending_changes", String.class))
                .isEqualTo("CANCELLED_BY_USER");
        List<OutboxRow> rows = outbox();
        assertThat(rows).hasSize(events + 1);
        assertThat(rows.getLast().eventType()).isEqualTo("apiKey.projection.update");
        assertThat(rows.getLast().version()).isEqualTo(3);
        assertThat(rows.getLast().apiKeyName()).isEqualTo("keep");

        dismissApiKey.handle(new DismissApiKeyCommand(owner, id));

        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.ACTIVE);
        assertThat(outbox()).hasSize(events + 1);
        assertThatThrownBy(() -> cancelApiKeyChange.handle(new CancelApiKeyChangeCommand(owner, id)))
                .as("nothing left to cancel").isInstanceOf(ApiKeyUpdateFailure.class);
    }

    // ------------------------------------------------------------------------------------------
    // Failed creation
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a rejected creation writes apiKey.projection.delete at revision 2 at once and is PROVISIONING_FAILED; dismiss removes the key and writes nothing")
    void failedCreation() {
        UUID id = issue(owner, "never", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();

        rejectChange.handle(new ApiKeyChangeRejected(id, 1, FailureReason.OUTBOX_PUBLISH_FAILURE));

        ApiKeyView failed = view(owner, id);
        assertThat(failed.state()).isEqualTo(ApiKeyState.PROVISIONING_FAILED);
        assertThat(jdbc.queryForObject("select failure_reason from access.pending_changes", String.class))
                .isEqualTo("OUTBOX_PUBLISH_FAILURE");
        assertThatThrownBy(() -> updateApiKey.handle(new UpdateApiKeyCommand(owner, id, "x", null, null)))
                .isInstanceOf(ApiKeyUpdateFailure.class);

        assertThat(outbox()).extracting(OutboxRow::eventType)
                .containsExactly("apiKey.projection.create", "apiKey.projection.delete");

        dismissApiKey.handle(new DismissApiKeyCommand(owner, id));

        List<OutboxRow> rows = outbox();
        assertThat(rows).extracting(OutboxRow::eventType).containsExactly("apiKey.projection.create", "apiKey.projection.delete");
        assertThat(rows.get(1).version()).isEqualTo(2);
        assertThat(rows.get(1).apiKeyId()).isEqualTo(id);
        assertThat(rows.get(1).permissions()).isEqualTo("{\"INGESTION\":\"WRITE\"}");
        assertThat(count("access.api_keys")).isZero();
        assertThat(count("access.pending_changes")).isZero();
        assertThat(count("access.pending_change_permissions")).isZero();
        assertThat(count("access.api_key_permissions")).isZero();
    }

    @Test
    @DisplayName("dismissing a healthy key is refused")
    void dismissActiveKeyRefused() {
        UUID id = issueActive(owner, "fine", Lifetime.NEVER, ingestionWrite()).key().apiKeyId();

        assertThatThrownBy(() -> dismissApiKey.handle(new DismissApiKeyCommand(owner, id)))
                .isInstanceOf(ApiKeyUpdateFailure.class);
        assertThat(view(owner, id).state()).isEqualTo(ApiKeyState.ACTIVE);
    }

    // ------------------------------------------------------------------------------------------

    private void assertVerifyFails(String accessId, String secret) {
        assertThatThrownBy(() -> verifyApiKey.handle(new VerifyApiKeyQuery(accessId, secret)))
                .isInstanceOf(ApiKeyVerificationFailure.class)
                .hasMessage(VERIFY_FAILURE);
    }

    private static String sha256Hex(String secret) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8)));
    }
}
