# Access Context

Ubiquitous language and rules of the Access context (supporting subdomain, control plane). It issues API keys and propagates them to the data plane (Ingestion) by control events. See [context-map.md](context-map.md).

## Vocabulary

| Term | Meaning |
|---|---|
| **API Key** | The aggregate. |
| **Owner** | The user (`userId`) who owns the key. Only the owner can read or change it. |
| **Access ID** | Public identifier, prefixed with the key format (e.g. `V1_...`). Safe to log. |
| **Secret** | Exists only at issuance, shown once, never stored. |
| **Secret Hash** | Stored form of the secret; propagated to the data plane. |
| **Credential** | Access ID + Secret. What a caller presents. |
| **Key Format** | `V1`, `V2`, ... Selects the generator/verifier. |
| **Issued Credential** | accessId + secret + hash, produced at issuance. Replaces `ApiKeyTriplet`. |
| **Name** | Owner-chosen label. |
| **Scope** | A resource area a key can be granted access to: `INGESTION`, `TENANT`, `CHANNEL`. Each scope lists the levels it accepts. |
| **Access Level** | `NONE` / `READ` / `WRITE` / `READ_WRITE`. `INGESTION` accepts only `NONE` and `WRITE`. |
| **Permissions** | An immutable map of scope to access level. A scope that is not present means `NONE`. Never contains nulls; equality ignores explicit `NONE`. |
| **Permission Changes** | A partial map: only the listed scopes change, an empty map changes nothing. Distinct from Permissions. |
| **Lifetime** | `ONE_DAY` / `ONE_MONTH` / `NEVER`. Replaces `ApiKeyExpiry.Duration`. |
| **Expiry** | An instant; null means never. Changing the lifetime restarts it from now. |

### Versioning

| Term | Meaning |
|---|---|
| **Key Format** | Credential scheme (`V1`). Not a change counter. |
| **Revision** | Single change counter: 1 at issuance, +1 per change. Merges old `issuedVersion` and `desiredVersion`. This is the `version` in events. |
| **Confirmed Revision** | Highest revision the data plane confirmed. 0 = never provisioned. Old `appliedVersion`. |
| **Lock version** | JPA optimistic locking; infrastructure only, not domain. |

### Changes

| Term | Meaning |
|---|---|
| **Pending Change** | At most one per key. Was `ApiKeyNextEntity`. |
| **Change Kind** | `CREATE` / `UPDATE` / `DELETE`. |
| **Change Status** | `PENDING` / `FAILED`. |
| **Propagation** | The aggregate registers the control event; a `BEFORE_COMMIT` listener writes it to the outbox in the same transaction (see Outbox below). |
| **Confirm / Reject** | Data plane's answer for a specific revision. Confirm applies the change; reject marks it failed. |
| **Dismiss** | Owner clears a failed change, or removes a key that is finished (`PROVISIONING_FAILED`, `DELETED`). Replaces user-facing "acknowledgeFailure". Nothing disappears without a dismiss. |
| **Deleted (tombstone)** | A confirmed delete. The key stays visible, cannot authenticate, and accepts no changes until the owner dismisses it. |
| **Failure Reason** | Exactly three: `REJECTED` (data plane nack), `CONFIRM_TIMEOUT` (no confirm/reject in time), `OUTBOX_PUBLISH_FAILURE`. |

## Commands

- **Issue**: create a key; revision 1, pending `CREATE`.
- **Update**: change name, permissions and/or lifetime; next revision, pending `UPDATE`.
- **Delete**: next revision, pending `DELETE`.
- **Dismiss**: clear a failed change.
- **Confirm(revision)**: data plane confirms that revision.
- **Reject(revision, reason)**: data plane rejects that revision.

## States

States are derived from the confirmed state and the pending change; they are not stored.

| State | Meaning |
|---|---|
| `PROVISIONING` | Creation pending. Owner can read it via GET; it cannot authenticate. |
| `PROVISIONING_FAILED` | Creation failed. |
| `ACTIVE` | Confirmed, no pending change. |
| `CHANGE_PENDING` | Update/delete awaiting confirmation. |
| `CHANGE_FAILED` | Update/delete failed; awaiting dismiss. Dismiss returns the key to `ACTIVE`. |
| `DELETED` | Delete confirmed. Tombstone: visible to the owner, cannot authenticate, awaiting dismiss. |

`EXPIRED` is a time-based condition independent of state: any key past its Expiry cannot authenticate.

```
 Issue --> PROVISIONING --confirm--> ACTIVE <---------------------+
               |                      |  ^                       |
          fail |               update/|  | confirm / dismiss     |
               v                delete|  |                       |
        PROVISIONING_FAILED           v  |                       |
               |               CHANGE_PENDING --fail--> CHANGE_FAILED
       dismiss |                      |                    |
               v                      | confirm(DELETE)    | dismiss
      removed + ApiKeyDeleted         v                    +--> ACTIVE
                                    DELETED --dismiss--> removed (no event)
```

"fail" means reject, `CONFIRM_TIMEOUT` or `OUTBOX_PUBLISH_FAILURE`.

## Behaviour decisions

- The owner **can** read a provisioning key.
- Dismissing a `PROVISIONING_FAILED` key removes it **and emits `ApiKeyDeleted`** (next revision), so the event stream stays consistent if the downstream service had actually applied the create.
- A confirmed delete does **not** remove the key. It becomes a visible `DELETED` tombstone and only the owner's dismiss removes it. That dismiss emits no event, because `ApiKeyDeleted` was already published when the delete was requested. A failed delete follows the normal failed-change path (`CHANGE_FAILED`, dismiss returns to `ACTIVE`).
- The Access ID stays reserved until the tombstone is dismissed.
- `ApiKeyCreated`, `ApiKeyUpdated`, `ApiKeyDeleted` keep their names, fields and event-type strings (`apiKey.created`, `apiKey.updated`, `apiKey.deleted`). They mean "revision N was requested and must be provisioned", not "took effect". Monitoring and Ingestion depend on them.

## Invariants

1. The secret is never stored.
2. At most one pending change per key. A failed change must be dismissed before any new change. The exceptions are dismissing a failed creation (removes the key and emits `ApiKeyDeleted`) and dismissing a `DELETED` tombstone (removes the key, no event). A `DELETED` key accepts no update or delete.
3. Verification uses confirmed state only. Provisioning keys cannot authenticate; a key with a pending update authenticates with the old permissions until confirmed.
4. Confirm/reject must name the pending revision; mismatches are refused.
5. An update that changes nothing creates no pending change and no event.
6. Only the owner can read or change a key.
7. Permissions never contain null, and every level is one its scope accepts (otherwise HTTP 400).
8. Unknown, pending, wrong-secret and expired Access IDs all produce the same `ApiKeyVerificationFailure` (no enumeration).

## Renames from old code

| Old | New |
|---|---|
| API Key (context) | Access (package `me.dhiren9939.backend.access`) |
| `ApiKeyEntity` | `ApiKey` aggregate (`domain`), no setters, behaviour methods |
| `ApiKeyTriplet` | `IssuedCredential` (`domain`) |
| `ApiKeyExpiry` service + `ApiKeyExpiry.Duration` | `Lifetime` enum with `expiryFrom(now)`, pure domain (`domain`) |
| `ApiKeyNextEntity` / `ApiKeyNextRepository` | `PendingChange`, owned by `ApiKey` (cascade, orphan removal), no repository |
| `ApiKeyUpdateType` (`CREATION`, `UPDATE`, `DELETE`) | `ChangeKind` (`CREATE`, `UPDATE`, `DELETE`) |
| `ApiKeyUpdateStatus` | `ChangeStatus` |
| `ApiKeyFailureReason` (`OUTBOX`, `NACK`, `TIMEOUT`) | `FailureReason` (`OUTBOX_PUBLISH_FAILURE`, `REJECTED`, `CONFIRM_TIMEOUT`) |
| `issuedVersion` + `desiredVersion` | `revision` |
| `appliedVersion` | `confirmedRevision` |
| (new) | `deletedAt` (tombstone), derived `ApiKeyState` |
| `new Permissions(null, null, null)` used as a patch | `ApiKeyChanges` + `PermissionChanges` (`domain`) |
| `Permissions(ingestionPermission, tenantPermission, channelPermission)` record with `@Embeddable` | `Permissions` map-form value object (`shared.access`, no JPA) over `Scope` -> `AccessLevel` |
| `ApiKeyPermissions.Ingestion` / `.Tenant` / `.Channel` enums | `Scope` enum (declares its allowed levels) + one `AccessLevel` enum |
| `ApiKeyRepository` (Spring Data) | `ApiKeys` port (`application.port.out`), `ApiKeysAdapter` + `JpaApiKeyRepository` (`adapter.out.persistence`) |
| `ApiKeyEventEmitterService` | removed; the aggregate registers events and `OutboxEventListener` (`adapter.out.persistence`) writes the outbox rows |
| `ApiKeyConfig` | `config.ApiKeyConfig` |
| `Sha256SecretHasher`, `SortableEncoder` | `adapter.out.keyformat` |
| `ApiKeyCreationProviderV1` + `ApiKeyCreationService` (`@Qualifier("creationV1")`) | `V1CredentialIssuer`; `IssueCredentialPort` bean picks the issuer named by `access.key-format.current` |
| `ApiKeyVerificationProviderV1` + `ApiKeyVerificationService` | `V1CredentialVerifier` (secret check only) behind the `CredentialVerifier` port; lookup, state and expiry checks live in `VerifyApiKeyHandler` and the aggregate |
| `ApiKeyToken` (mutable, `verified` flag) | immutable `ParsedApiKey` (internal) -> `VerifiedApiKey` |
| `ApiKeyDto` (nullable secret and hash) | `ApiKeyView` (no secrets) and `IssuedApiKeyResult` (secret, once) |
| `ApiKeyService`, `ApiKeyPersistenceService` | one `*UseCase` + `*Handler` per operation |
| `ApiKeyController` / `ApiKeyInternalController` in `web` | `adapter.in.web`, depend on input ports only |
| `ApiKeyFacadeImpl` | `adapter.in.facade.ApiKeyFacadeImpl`, calls `VerifyApiKeyUseCase` |
| `acknowledgeFailure` (user-facing) | Dismiss: `POST /api/v1/user/{userId}/apikey/{apiKeyId}/dismiss` |
| six `/internal/apikey/{id}/{creation,update,deletion}/{success,failure}` endpoints | `POST /internal/apikey/{id}/changes/{revision}/confirm` and `/reject` (optional body `{"reason": ...}`, default `REJECTED`) |
| schema `api_key`, tables `api_keys`, `api_key_next`, `outbox` | schema `access`, tables `api_keys`, `pending_changes`, `outbox` |
| Delivery Service (other context) | Dispatcher |

GET and create responses gain a derived `state` field (`ApiKeyState`); `updateStatus` stays.

## Package structure

Pragmatic clean architecture: the dependency rule is `adapter` -> `application` -> `domain`. The aggregate is a rich JPA entity, so `jakarta.persistence` is the only framework import allowed in `domain`.

```
me.dhiren9939.backend
├── shared/access/                public contracts, no behaviour
│   ├── events/                   ApiKeyCreated, ApiKeyUpdated, ApiKeyDeleted, ApiKeyEvent
│   ├── Permissions, Scope, AccessLevel, InvalidPermissionsException
│   └── ApiKeyFacade, ApiKeyVerifyDto, ApiKeyVerificationFailure, ApiKeyParsingFailure
└── access/                       the Access bounded context (holds no classes at its root)
    ├── domain/                   ApiKey aggregate, PendingChange, ApiKeyState, ChangeKind, ChangeStatus,
    │                             FailureReason, IssuedCredential, Lifetime, ApiKeyChanges, PermissionChanges,
    │                             DesiredState, DismissResult, ApiKeyNotFoundException, ApiKeyUpdateFailure
    ├── application/
    │   ├── port/in/              one use case per operation, plus its command/query and result records;
    │   │                         ApiKeyView (secret-free read model), VerifiedApiKey
    │   ├── port/out/             ApiKeys (repository), IssueCredentialPort, CredentialVerifier
    │   └── usecase/              one package-private *Handler per input port, OptimisticLockRetry, ParsedApiKey
    ├── adapter/
    │   ├── in/web/               ApiKeyController, ApiKeyInternalController, ApiKeyExceptionHandler, DTOs
    │   ├── in/facade/            ApiKeyFacadeImpl (in-process verification entry point)
    │   ├── out/persistence/      ApiKeysAdapter, JpaApiKeyRepository, outbox entity + repository, OutboxEventListener
    │   └── out/keyformat/        CredentialIssuer, Sha256SecretHasher, SortableEncoder, v1/ issuer + verifier
    └── config/                   ApiKeyConfig (Clock, SecureRandom, selects the current key format)
```

Rules, enforced by `ArchitectureTest` (ArchUnit):

- `domain` depends only on itself, `shared`, `java.*`, `jakarta.persistence`, lombok, the UUID generator and JSpecify; no Spring.
- `application` never depends on `adapter` or `config`.
- Inbound adapters never depend on outbound adapters, the handlers or the output ports; they talk to input ports only.
- Outbound adapters never depend on inbound adapters or handlers.
- Input ports are implemented only in `application.usecase`, and handlers are not public.
- `shared` never depends on `access`.
- No field injection; no package cycles inside `access`.

`shared/access` is what other contexts may depend on. It is free of JPA, Spring and JSON libraries (enforced by ArchUnit); `Permissions` is a plain value object.

The key format used for new credentials is `access.key-format.current` (default `V1`). Verification supports every registered format, so older keys keep working after the current one moves on.

## Use cases

Each is an input port in `application.port.in` with one handler in `application.usecase`. Handlers load the aggregate through `ApiKeys`, call one aggregate method and save; they hold no business rules.

| Use case | Command / query | Result |
|---|---|---|
| `IssueApiKeyUseCase` | `IssueApiKeyCommand(userId, name, lifetime, permissions)` | `IssuedApiKeyResult(key, accessSecret)`; the only type that carries the secret |
| `GetApiKeyUseCase` | `GetApiKeyQuery(userId, apiKeyId)` | `ApiKeyView` |
| `UpdateApiKeyUseCase` | `UpdateApiKeyCommand(userId, apiKeyId, name?, lifetime?, permissions?)` | `ApiKeyView`; runs in its own transaction with an optimistic-lock retry |
| `DeleteApiKeyUseCase` | `DeleteApiKeyCommand(userId, apiKeyId)` | none |
| `DismissApiKeyUseCase` | `DismissApiKeyCommand(userId, apiKeyId)` | none |
| `ConfirmChangeUseCase` | `ConfirmChangeCommand(apiKeyId, revision)` | none |
| `RejectChangeUseCase` | `RejectChangeCommand(apiKeyId, revision, reason)` | none |
| `VerifyApiKeyUseCase` | `VerifyApiKeyQuery(accessId, accessSecret)` | `VerifiedApiKey` |

Output ports: `ApiKeys` (load and store aggregates), `IssueCredentialPort` (new credential in the current key format), `CredentialVerifier` (secret vs stored hash, per key format).

Failures are mapped to HTTP by `ApiKeyExceptionHandler`: not found -> 404, refused change -> 409, verification failure -> 401, malformed access id -> 400.

## Known bugs fixed by the refactor

1. The verification provider caught `NoSuchElementException`, but the lookup threw `ApiKeyNotFoundException`.
2. Acks (confirm/reject) carried no revision, so a late ack could apply to the wrong change.

## Outbox

Events are registered by the `ApiKey` aggregate itself (`issue`, `requestUpdate`, `requestDeletion`, and `dismiss` of a failed creation) and collected with `pullDomainEvents()`. `ApiKeysAdapter` publishes them through Spring's `ApplicationEventPublisher` after `save`/`delete`. `OutboxEventListener` handles them in `@TransactionalEventListener(phase = BEFORE_COMMIT)` and saves the outbox row in the same transaction, so if the row cannot be written the key change rolls back too. Event types are unchanged.

`ApiKeyEvent` is a `sealed interface` that permits exactly `ApiKeyCreated`, `ApiKeyUpdated` and `ApiKeyDeleted`. Adding an event type means adding it to `permits`, which makes the listener's exhaustive `switch` fail to compile until it is handled.

### Event timestamps and fields

| Field | Meaning |
|---|---|
| `occurredAt` | When the change reached the outbox. Set by the aggregate from the command's `now`. Never null. Outbox column `occurred_at NOT NULL`. |
| `emittedAt` | When the event was published into the stream. The aggregate cannot know this, so registered events carry `null`, and so does the outbox row until a relay publishes it. Outbox column `emitted_at` is nullable. |
| `createdAt` | When the key was issued. Unchanged. |

`expiresAt` was **removed** from `ApiKeyCreated`, `ApiKeyUpdated`, `ApiKeyDeleted` and `ApiKeyEvent` (user decision, 2026-11-10). The aggregate still has its own expiry, and `canAuthenticate(now)` enforces it inside Access.

### Decision: key expiry is not propagated to the data plane

The old `expiresAt` on events mixed an event-lifetime idea with the key's own expiry, so it was removed. Key expiry is **not** propagated: no `ApiKeyExpired` event, no expiry scheduler, and Ingestion's projection does not enforce expiry (user decision, 2026-11-10: "on expiry we don't care"). Access still rejects expired keys in its own verification (`canAuthenticate(now)`). Revisit only if the data plane must refuse expired keys.

## Permissions: scopes and levels

`Permissions` maps `Scope` to `AccessLevel`. Absent means `NONE`; consumers must also treat a scope they do not know as `NONE`.

| Scope | Allowed levels |
|---|---|
| `INGESTION` | `NONE`, `WRITE` |
| `TENANT` | `NONE`, `READ`, `WRITE`, `READ_WRITE` |
| `CHANNEL` | `NONE`, `READ`, `WRITE`, `READ_WRITE` |

**Adding a scope** (for example `MONITORING`) is one new constant in `shared/access/Scope.java`, listing the levels it accepts beyond `NONE`:

```java
MONITORING(AccessLevel.READ)
```

Nothing else changes: updates (`PermissionChanges`), persistence, the web layer, the events and the validation all work from the enum. `ScopeTest` runs over `Scope.values()`, so a badly declared scope fails the build. Existing keys simply have `NONE` for it.

**Wire shapes** (scope and level names are the values; renaming one is a contract change):
- REST request/response `permissions` is a JSON object keyed by scope name. Create and update accept a partial object; responses list every scope, with `NONE` where nothing was granted. A level the scope does not accept, or an unknown scope or level name, is HTTP 400.
- Events (`ApiKeyCreated`, `ApiKeyUpdated`, `ApiKeyDeleted`) carry `permissions` as the same object but list only granted scopes, for example `{"INGESTION":"WRITE","TENANT":"READ"}`; `{}` means no access. The outbox stores exactly that string in `access.outbox.permissions`.

**Storage:** `access.api_key_permissions` and `access.pending_change_permissions` hold one row per granted scope (`scope`, `access_level`); the outbox keeps the JSON text. `JpaMappingTest` checks these mappings offline; they have not run against a real Postgres.

## Integration tests (real PostgreSQL)

Tests extending `PostgresIntegrationTest` (package `me.dhiren9939.backend.integration`) run against a **throwaway `postgres:17` container started by Testcontainers**, once per JVM. They never connect to the dev database from `docker-compose.yml` (`maindb` on `localhost:5432`) and leave it untouched. Docker must be running.

- All tests, unit and integration: `./mvnw test` (about a minute).
- Only integration tests: `./mvnw test -Dgroups=integration`.
- Skip them for a fast loop: `./mvnw test -Dgroups='!integration'`.

What they cover: schema creation with the app's real config (`ddl-auto=update`); the key lifecycle through the input ports; outbox rows and payloads; orphan removal; atomicity (a database trigger makes the outbox write fail and the key change must roll back); concurrent updates; the HTTP surface with error mapping; and `SqlMigrationValidateIntegrationTest`, which applies only `db.migration/access/V1__create_access_schema.sql` to a fresh database and boots with `ddl-auto=validate`.

Bugs these tests found and fixed: instant columns in the SQL file were `TIMESTAMP` instead of `TIMESTAMPTZ` (Hibernate's validator does not notice, so the test also checks column types and a time-zone round trip), and `POST` without a name or expiry returned 500 instead of 400 (request validation added).
