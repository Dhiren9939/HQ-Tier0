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
| **Propagation** | The aggregate registers the control event; `ApiKeysAdapter` writes it to the outbox in the same transaction (see Outbox below). |
| **Confirm / Reject** | Data plane's answer for a specific revision. Confirm applies the change; reject marks it failed. |
| **Dismiss** | The owner acknowledges that they understand the state of their key: it clears a failed change, or removes a key that is finished (`PROVISIONING_FAILED`, `DELETED`). It never emits anything; compensating commands go out when the change fails. Replaces user-facing "acknowledgeFailure". Nothing disappears without a dismiss. |
| **Cancel** | The owner withdraws a change that is still pending. It fails with `CANCELLED_BY_USER` and is compensated at once, like any failure. |
| **Incoming events** | What the data plane sends back, as plain records in `shared/access/events/inbound`: `ApiKeyChangeConfirmed(apiKeyId, version, issuer)` (it applied that revision) and `ApiKeyChangeRejected(apiKeyId, version, issuer, failureReason)` (that revision failed). They are the inputs of `ConfirmChangeUseCase` and `RejectChangeUseCase`; the internal HTTP controller builds them from the path and body. `issuer` is a string naming the downstream service that answered, like the `iss` claim of a JWT. It is validated (required, non-blank) and logged, not persisted. With more than one downstream service, the confirm and reject use cases are where "every issuer has answered" would be enforced. Access does not emit them and writes nothing to the outbox for them. A reject does emit the compensating projection command. |
| **Compensating command** | Emitted the moment a change fails, at the next revision, because the data plane may have applied it anyway: failed create -> `DeleteApiKeyProjectionCommand`; failed update -> `UpdateApiKeyProjectionCommand` restating the confirmed state; failed delete -> `CreateApiKeyProjectionCommand` restoring the key (it carries the secret hash, so it also works when the key is gone downstream). |
| **Deleted (tombstone)** | A confirmed delete. The key stays visible, cannot authenticate, and accepts no changes until the owner dismisses it. |
| **Failure Reason** | Exactly four: `REJECTED` (data plane nack), `CONFIRM_TIMEOUT` (no confirm/reject in time), `OUTBOX_PUBLISH_FAILURE`, `CANCELLED_BY_USER`. |

## Commands

- **Issue**: create a key; revision 1, pending `CREATE`.
- **Update**: change name, permissions and/or lifetime; next revision, pending `UPDATE`.
- **Delete**: next revision, pending `DELETE`.
- **Dismiss**: acknowledge a failed or finished key; emits nothing.
- **Cancel**: withdraw the pending change (fails it as `CANCELLED_BY_USER`).
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
      removed + DeleteApiKeyProjectionCommand         v                    +--> ACTIVE
                                    DELETED --dismiss--> removed (no event)
```

"fail" means reject, `CONFIRM_TIMEOUT`, `OUTBOX_PUBLISH_FAILURE` or `CANCELLED_BY_USER`, and each one emits the compensating command immediately. Dismiss (shown in the diagram) only acknowledges.

## Behaviour decisions

- The owner **can** read a provisioning key.
- A change that fails (reject, timeout, outbox failure or user cancel) emits its compensating command **immediately**, at the next revision, so the event stream stays consistent if the downstream service had actually applied it. Dismissing only records that the owner has seen the failure and emits nothing.
- A confirm that arrives after the change already failed is refused (409), because the revision is no longer pending.
- A confirmed delete does **not** remove the key. It becomes a visible `DELETED` tombstone and only the owner's dismiss removes it. That dismiss emits no event, because `DeleteApiKeyProjectionCommand` was already published when the delete was requested. A failed delete follows the normal failed-change path (`CHANGE_FAILED`, dismiss returns to `ACTIVE`).
- The Access ID stays reserved until the tombstone is dismissed.
- `CreateApiKeyProjectionCommand`, `UpdateApiKeyProjectionCommand`, `DeleteApiKeyProjectionCommand` carry the type strings (`apiKey.projection.create`, `apiKey.projection.update`, `apiKey.projection.delete`). They mean "revision N was requested and must be provisioned", not "took effect". Monitoring and Ingestion depend on them.

## Invariants

1. The secret is never stored.
2. At most one pending change per key. A failed change must be dismissed before any new change. The exceptions are dismissing a failed creation and dismissing a `DELETED` tombstone (both remove the key and emit nothing). A `DELETED` key accepts no update or delete.
3. Verification uses confirmed state only. Provisioning keys cannot authenticate; a key with a pending update authenticates with the old permissions until confirmed.
4. Confirm/reject must name the pending revision; mismatches are refused.
5. An update that changes nothing creates no pending change and no event.
6. Only the owner can read or change a key.
7. Permissions never contain null, and every level is one its scope accepts (otherwise HTTP 400).
8. Verification failures carry a machine-readable `VerificationFailureCode` and an end-user-safe message. Unknown Access IDs and wrong secrets are one outcome (`INVALID_CREDENTIALS`); the secret is checked before the key's state, so `KEY_EXPIRED`, `KEY_DELETED` and `KEY_NOT_ACTIVE` are only revealed to someone who holds the right secret (no enumeration).

## Renames from old code

| Old | New |
|---|---|
| API Key (context) | Access (package `me.dhiren9939.backend.access`) |
| `ApiKeyEntity` | `ApiKey` aggregate (`domain.aggregates.apikey`), no setters, behaviour methods |
| `ApiKeyTriplet` | `IssuedCredential` (`domain.aggregates.apikey`) |
| `ApiKeyExpiry` service + `ApiKeyExpiry.Duration` | `Lifetime` enum with `expiryFrom(now)`, pure domain (`domain.aggregates.apikey`) |
| `ApiKeyNextEntity` / `ApiKeyNextRepository` | `PendingChange`, owned by `ApiKey` (cascade, orphan removal), no repository |
| `ApiKeyUpdateType` (`CREATION`, `UPDATE`, `DELETE`) | `ChangeKind` (`CREATE`, `UPDATE`, `DELETE`; in `domain.aggregates.apikey`) |
| `ApiKeyUpdateStatus` | `ChangeStatus` |
| `ApiKeyFailureReason` (`OUTBOX`, `NACK`, `TIMEOUT`) | `FailureReason` (`OUTBOX_PUBLISH_FAILURE`, `REJECTED`, `CONFIRM_TIMEOUT`, plus new `CANCELLED_BY_USER`; in `shared.access.events.inbound`, because the incoming rejected event carries it) |
| `issuedVersion` + `desiredVersion` | `revision` |
| `appliedVersion` | `confirmedRevision` |
| (new) | `deletedAt` (tombstone), derived `ApiKeyState` |
| `new Permissions(null, null, null)` used as a patch | `ApiKeyChanges` + `PermissionChanges` (`domain.aggregates.apikey`) |
| `Permissions(ingestionPermission, tenantPermission, channelPermission)` record with `@Embeddable` | `Permissions` map-form value object (`shared.access`, no JPA) over `Scope` -> `AccessLevel` |
| `ApiKeyPermissions.Ingestion` / `.Tenant` / `.Channel` enums | `Scope` enum (declares its allowed levels) + one `AccessLevel` enum |
| `ApiKeyRepository` (Spring Data) | `ApiKeys` (`domain.repositories`), `ApiKeysAdapter` + `JpaApiKeyRepository` (`infrastructure.persistence.repositories`) |
| `ApiKeyEventEmitterService` | removed; the aggregate registers projection commands and `ApiKeysAdapter` (`infrastructure.persistence.repositories`) writes the outbox rows |
| `ApiKeyConfig` | `infrastructure.config.ApiKeyConfig` |
| `Sha256SecretHasher`, `SortableEncoder` | `infrastructure.keyformat` |
| `ApiKeyCreationProviderV1` + `ApiKeyCreationService` (`@Qualifier("creationV1")`) | `V1CredentialIssuer`; `IssueCredentialPort` bean picks the issuer named by `access.key-format.current` |
| `ApiKeyVerificationProviderV1` + `ApiKeyVerificationService` | `V1CredentialVerifier` (secret check only) behind the `CredentialVerifier` interface (`application.interfaces`); lookup, state and expiry checks live in `VerifyApiKeyHandler` and the aggregate |
| `ApiKeyToken` (mutable, `verified` flag) | immutable `ParsedApiKey` (internal) -> `VerifiedApiKey` |
| `ApiKeyDto` (nullable secret and hash) | `ApiKeyView` (no secrets) and `IssuedApiKeyResult` (secret, once) |
| `ApiKeyService`, `ApiKeyPersistenceService` | one `*UseCase` + `*Handler` per operation, each in its own `application.usecase.<name>` package |
| `ApiKeyController` / `ApiKeyInternalController` in `web` | `presentation.api.controllers`, depend on the use cases only; DTOs in `presentation.api.dtos` |
| `ApiKeyFacadeImpl` | `presentation.facade.ApiKeyFacadeImpl`, calls `VerifyApiKeyUseCase` |
| `acknowledgeFailure` (user-facing) | Dismiss: `POST /api/v1/user/{userId}/apikey/{apiKeyId}/dismiss`; new: Cancel `POST /api/v1/user/{userId}/apikey/{apiKeyId}/cancel` |
| six `/internal/apikey/{id}/{creation,update,deletion}/{success,failure}` endpoints | `POST /internal/apikey/{id}/changes/{revision}/confirm` and `/reject` (required body `{"issuer": "..."}`; reject also takes an optional `"reason"`, default `REJECTED`; `issuer` names the downstream service answering, like a JWT `iss`; it is logged, not stored, so a second downstream service can later be required to answer too) (a Postman test harness; in production these are service-to-service messages calling the same use cases) |
| schema `api_key`, tables `api_keys`, `api_key_next`, `outbox` | schema `access`, tables `api_keys`, `pending_changes`, `outbox` |
| Delivery Service (other context) | Dispatcher |

Responses carry one status field, `status`, a presentation enum (`ApiKeyStatus`) derived from the internal `ApiKeyState` and the kind of the unsettled change: `CREATING`, `CREATION_FAILED`, `ACTIVE`, `UPDATING`, `UPDATE_FAILED`, `DELETING`, `DELETE_FAILED` or `DELETED` (a confirmed delete shows as `DELETED` until dismissed). It says what is happening and whether it failed, never why: no revisions, failure reasons, pending-change data, version counters or secret hashes are exposed, and the internal states can change without breaking clients.

## Package structure

Pragmatic clean architecture: the layers are `domain`, `application`, `presentation` and `infrastructure`, and the dependency rule is `presentation` and `infrastructure` -> `application` -> `domain`. The aggregate is a rich JPA entity, so `jakarta.persistence` is the only framework import allowed in `domain`.

```
me.dhiren9939.backend
├── shared/access/                public contracts, no behaviour
│   ├── events/outbound/            ApiKeyProjectionCommand (sealed) with Create/Update/DeleteApiKeyProjectionCommand (outgoing)
│   ├── events/inbound/           ApiKeyChangeConfirmed, ApiKeyChangeRejected, FailureReason (incoming from the data plane)
│   ├── Permissions, Scope, AccessLevel, InvalidPermissionsException
│   └── ApiKeyFacade, ApiKeyVerifyDto, ApiKeyVerificationFailure, VerificationFailureCode
└── access/                       the Access bounded context (holds no classes at its root)
    ├── domain/
    │   ├── aggregates/apikey/    ApiKey aggregate, PendingChange, ApiKeyState, ChangeStatus, IssuedCredential,
    │   │                         Lifetime, ApiKeyChanges, PermissionChanges, DesiredState, DismissResult
    │   ├── exceptions/           ApiKeyNotFoundException, ApiKeyUpdateFailure
    │   └── repositories/         ApiKeys (the storage contract; the adapter that implements it is in infrastructure)
    ├── application/
    │   ├── usecase/              user/<name> (issue, get, update, delete, dismiss, cancel: what the key's owner asks for)
    │   │                         and service/<name> (confirm, reject, verify: what other services ask for). Each holds the *UseCase interface,
    │   │                         its *Command/*Query and result record, and the package-private *Handler; verify also holds ParsedApiKey and VerifiedApiKey
    │   ├── model/                ApiKeyView (secret-free read model shared by several use cases)
    │   ├── transaction/          RetriedTransaction, OptimisticLockRetry
    │   └── interfaces/           IssueCredentialPort, CredentialVerifier
    ├── presentation/
    │   ├── api/controllers/      ApiKeyController, ApiKeyInternalController, ApiKeyExceptionHandler
    │   ├── api/dtos/             request and response DTOs
    │   └── facade/               ApiKeyFacadeImpl (in-process verification entry point)
    └── infrastructure/
        ├── persistence/
        │   ├── models/           EventOutboxEntity
        │   └── repositories/     ApiKeysAdapter, JpaApiKeyRepository, EventOutboxRepository
        ├── keyformat/            CredentialIssuer, Sha256SecretHasher, SortableEncoder, v1/ issuer + verifier
        └── config/               ApiKeyConfig (Clock, SecureRandom, selects the current key format)
```

Rules, enforced by `ArchitectureTest` (ArchUnit, 12 tests):

- `domain` (including `domain.repositories`) depends only on itself, `shared`, `java.*`, `jakarta.persistence`, lombok, the UUID generator and JSpecify; no Spring.
- `application` never depends on `presentation` or `infrastructure`.
- The layers only point inwards (ArchUnit layered architecture): nothing depends on `presentation` or `infrastructure`; `application` is used only by those two; `domain` is used only by `application`, `presentation` and `infrastructure`.
- `presentation` never depends on `infrastructure`, `domain.repositories` or `application.interfaces`; it talks to the use cases.
- `infrastructure` never depends on `presentation` or on `application.usecase`.
- Use case interfaces are implemented only inside `application.usecase`, and `*Handler` classes are not public.
- `shared` is free of frameworks (only `java.*` and lombok) and never depends on `access`.
- No field injection; no package cycles among `domain`, `application`, `presentation` and `infrastructure`.

`shared/access` is what other contexts may depend on. It is free of JPA, Spring and JSON libraries (enforced by ArchUnit); `Permissions` is a plain value object.

The key format used for new credentials is `access.key-format.current` (default `V1`). Verification supports every registered format, so older keys keep working after the current one moves on.

## Use cases

Each is a use case interface in its own package `application.usecase.<name>` with one package-private handler beside it. Handlers load the aggregate through `ApiKeys`, call one aggregate method and save; they hold no business rules.

| Use case | Command / query | Result |
|---|---|---|
| `IssueApiKeyUseCase` | `IssueApiKeyCommand(userId, name, lifetime, permissions)` | `IssuedApiKeyResult(key, accessSecret)`; the only type that carries the secret |
| `GetApiKeyUseCase` | `GetApiKeyQuery(userId, apiKeyId)` | `ApiKeyView` |
| `UpdateApiKeyUseCase` | `UpdateApiKeyCommand(userId, apiKeyId, name?, lifetime?, permissions?)` | `ApiKeyView`; runs in its own transaction with an optimistic-lock retry |
| `DeleteApiKeyUseCase` | `DeleteApiKeyCommand(userId, apiKeyId)` | none |
| `DismissApiKeyUseCase` | `DismissApiKeyCommand(userId, apiKeyId)` | none |
| `CancelApiKeyChangeUseCase` | `CancelApiKeyChangeCommand(userId, apiKeyId)` | none |
| `ConfirmChangeUseCase` | incoming event `ApiKeyChangeConfirmed(apiKeyId, version, issuer)` | none |
| `RejectChangeUseCase` | incoming event `ApiKeyChangeRejected(apiKeyId, version, issuer, failureReason)` | none |
| `VerifyApiKeyUseCase` | `VerifyApiKeyQuery(accessId, accessSecret)` | `VerifiedApiKey` |

Output abstractions: `ApiKeys` (in `domain.repositories`: load and store aggregates), `IssueCredentialPort` (new credential in the current key format), `CredentialVerifier` (secret vs stored hash, per key format); both of these are in `application.interfaces`.

Failures are mapped to HTTP by `ApiKeyExceptionHandler` (`presentation.api.controllers`): not found -> 404, refused change -> 409, verification failure -> 401, malformed access id -> 400.

## Known bugs fixed by the refactor

1. The verification provider caught `NoSuchElementException`, but the lookup threw `ApiKeyNotFoundException`.
2. Acks (confirm/reject) carried no revision, so a late ack could apply to the wrong change.

## Outbox

Projection commands are registered by the `ApiKey` aggregate itself (`issue`, `requestUpdate`, `requestDeletion`, and `reject`/`cancelPendingChange`, which register the compensating command) and collected with `pullProjectionCommands()`. `ApiKeysAdapter` writes each one to the outbox itself (`EventOutboxEntity.of(event)`) right after `save`/`delete`, in the caller's transaction, so if the row cannot be written the key change rolls back too. Confirming a change registers nothing; the confirm and reject coming in from the data plane are the incoming events described above, not outbox entries. Event types are `apiKey.projection.create`, `apiKey.projection.update` and `apiKey.projection.delete`.

`ApiKeyProjectionCommand` is a `sealed interface` that permits exactly `CreateApiKeyProjectionCommand`, `UpdateApiKeyProjectionCommand` and `DeleteApiKeyProjectionCommand`. Adding an event type means adding it to `permits`, which makes the listener's exhaustive `switch` fail to compile until it is handled.

### Event timestamps and fields

| Field | Meaning |
|---|---|
| `occurredAt` | When the change reached the outbox. Set by the aggregate from the command's `now`. Never null. Outbox column `occurred_at NOT NULL`. |
| `emittedAt` | When the event was published into the stream. The aggregate cannot know this, so registered events carry `null`, and so does the outbox row until a relay publishes it. Outbox column `emitted_at` is nullable. |
| `createdAt` | When the key was issued. Unchanged. |

`expiresAt` was **removed** from `CreateApiKeyProjectionCommand`, `UpdateApiKeyProjectionCommand`, `DeleteApiKeyProjectionCommand` and `ApiKeyProjectionCommand` (user decision, 2026-11-10). The aggregate still has its own expiry, and `canAuthenticate(now)` enforces it inside Access.

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
- Events (`CreateApiKeyProjectionCommand`, `UpdateApiKeyProjectionCommand`, `DeleteApiKeyProjectionCommand`) carry `permissions` as the same object but list only granted scopes, for example `{"INGESTION":"WRITE","TENANT":"READ"}`; `{}` means no access. The outbox stores exactly that string in `access.outbox.permissions`.

**Storage:** `access.api_key_permissions` and `access.pending_change_permissions` hold one row per granted scope (`scope`, `access_level`); the outbox keeps the JSON text. `JpaMappingTest` checks these mappings offline; they have not run against a real Postgres.

## Integration tests (real PostgreSQL)

Tests extending `PostgresIntegrationTest` (package `me.dhiren9939.backend.integration`) run against a **throwaway `postgres:17` container started by Testcontainers**, once per JVM. They never connect to the dev database from `docker-compose.yml` (`maindb` on `localhost:5432`) and leave it untouched. Docker must be running.

- All tests, unit and integration: `./mvnw test` (about a minute).
- Only integration tests: `./mvnw test -Dgroups=integration`.
- Skip them for a fast loop: `./mvnw test -Dgroups='!integration'`.

What they cover: schema creation with the app's real config (`ddl-auto=update`); the key lifecycle through the use case interfaces; outbox rows and payloads; orphan removal; atomicity (a database trigger makes the outbox write fail and the key change must roll back); concurrent updates; the HTTP surface with error mapping; and `SqlMigrationValidateIntegrationTest`, which applies only `db.migration/access/V1__create_access_schema.sql` to a fresh database and boots with `ddl-auto=validate`.

Bugs these tests found and fixed: instant columns in the SQL file were `TIMESTAMP` instead of `TIMESTAMPTZ` (Hibernate's validator does not notice, so the test also checks column types and a time-zone round trip), and `POST` without a name or expiry returned 500 instead of 400 (request validation added).
