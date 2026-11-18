# Access

Ubiquitous language, rules, code map and tests for the Access context (supporting subdomain, control plane). It issues API keys and publishes them to the data plane (Ingestion) as projection events: **Access is the source of truth, it applies every change at once and the data plane conforms to the published events.** This is the single Access document; see [context-map.md](context-map.md) for how the context fits with the others.

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
| **Revision** | Per-key change counter: 1 at issuance, +1 per landed change. It is the `version` in events and gives the data plane a total order per key for ordering and idempotency. |
| **Lock version** | JPA optimistic locking; infrastructure only, not domain. |

### Changes

| Term | Meaning |
|---|---|
| **Propagation** | The aggregate registers the control event; `ApiKeysAdapter` writes it to the outbox in the same transaction (see Outbox below). |
| **Apply-at-once** | Every command (issue, update, delete) takes effect on the aggregate the moment it is accepted. There is no pending state and nothing to wait for. |
| **Event version** | The revision stamped on each projection event. Events for one key are unique and strictly increasing, so a consumer can drop duplicates and reorder safely. |
| **Hard delete** | Deleting removes the key's row at once. Nothing is kept as a tombstone, the key is immediately gone from reads, and its Access ID is free to issue again. The `ApiKeyDeleted` event still goes to the outbox in the same transaction. |

## Commands

- **Issue**: create a key; revision 1, `ApiKeyCreated` event. The key is `ACTIVE` at once.
- **Update**: change name, permissions and/or lifetime; revision +1 and an `ApiKeyUpdated` event. An update that changes nothing does nothing (no revision, no event).
- **Delete**: the key is removed at once; revision +1 and an `ApiKeyDeleted` event in the same transaction, then the row is deleted.

## States

States are derived, not stored: from the expiry and the clock.

| State | Meaning |
|---|---|
| `ACTIVE` | Issued and in force; authenticates until it expires. |
| `EXPIRED` | The expiry has passed. It can be renewed by an update that extends the lifetime. |

```
 Issue --> ACTIVE --update--> ACTIVE or EXPIRED (revision +1)
             |
        expiry passes, or an update renews it
             v
          EXPIRED --update (renew)--> ACTIVE

 any key --delete--> removed (row deleted; ApiKeyDeleted written to the outbox)
```

Every command applies immediately. Update bumps the revision; delete removes the key and writes its event in the
same transaction. A deleted key has no state: it is simply not found.

## Behaviour decisions

- **HQ is the source of truth.** A change is applied to the aggregate as soon as the command is accepted and the event is written to the outbox in the same transaction. The data plane reads the events and conforms; nothing waits for its answer, and there is no confirm, reject or cancel handshake.
- A deleted key **does** disappear. The row is removed in the same transaction that writes its `ApiKeyDeleted` event; there is no tombstone, no `DELETED` state and nothing to dismiss. Reads, updates and further deletes then report not found.
- The Access ID is free again as soon as the delete lands; it can be issued to a new key immediately.
- `CreateApiKeyProjectionCommand`, `UpdateApiKeyProjectionCommand`, `DeleteApiKeyProjectionCommand` carry the type strings (`apiKey.projection.create`, `apiKey.projection.update`, `apiKey.projection.delete`) and the key's `version` (the revision). Events for one key are unique and strictly increasing by version.

## Invariants

1. The secret is never stored.
2. An update that changes nothing does nothing: no revision bump, no event.
3. A delete removes the key's row and registers `ApiKeyDeleted` in the same transaction; there is no tombstone and the Access ID is reusable at once.
4. Verification succeeds only for `ACTIVE` keys. The secret is checked before the key's state, so `KEY_EXPIRED` is only revealed to someone who holds the right secret (no enumeration).
5. Only the owner can read or change a key.
6. Permissions never contain null, and every level is one its scope accepts (otherwise HTTP 400).
7. Every projection event carries the key's revision as its `version`; versions of one key are unique and strictly increasing.

## Renames from old code

| Old | New |
|---|---|
| API Key (context) | Access (package `me.dhiren9939.backend.access`) |
| `ApiKeyEntity` | `ApiKey` aggregate (`domain.aggregates.apikey`), no setters, behaviour methods |
| `ApiKeyTriplet` | `IssuedCredential` (`domain.aggregates.apikey`) |
| `ApiKeyExpiry` service + `ApiKeyExpiry.Duration` | `Lifetime` enum with `expiryFrom(now)`, pure domain (`domain.aggregates.apikey`) |
| `ApiKeyNextEntity` / `ApiKeyNextRepository` (pending-change handshake) | removed; the apply-at-once model has no pending change |
| `ApiKeyUpdateType` (`CREATION`, `UPDATE`, `DELETE`) | removed; the command itself says what happens (`update`, `delete`) |
| `ApiKeyUpdateStatus` | removed |
| `ApiKeyFailureReason` (`OUTBOX`, `NACK`, `TIMEOUT`) | removed; nothing fails asynchronously any more |
| `issuedVersion` + `desiredVersion` | `revision` |
| `appliedVersion` | removed; the revision is both the key's and the event's version |
| (new) | derived `ApiKeyState` (`ACTIVE`, `EXPIRED`) |
| `new Permissions(null, null, null)` used as a patch | `ApiKeyChanges` + `PermissionChanges` (`domain.aggregates.apikey`) |
| `Permissions(ingestionPermission, tenantPermission, channelPermission)` record with `@Embeddable` | `Permissions` map-form value object (`domain.aggregates.apikey`, no JPA) over `Scope` -> `AccessLevel` |
| `ApiKeyPermissions.Ingestion` / `.Tenant` / `.Channel` enums | `Scope` enum (declares its allowed levels) + one `AccessLevel` enum (both in `domain.aggregates.apikey`) |
| `ApiKeyRepository` (Spring Data) | `ApiKeys` (`domain.repositories`), `ApiKeysAdapter` + `JpaApiKeyRepository` (`infrastructure.persistence.repositories`) |
| `ApiKeyEventEmitterService` | removed; the aggregate registers domain events, `ApiKeysAdapter` (`infrastructure.persistence.repositories`) maps them with `ApiKeyProjectionMapper` and writes the outbox rows |
| `ApiKeyConfig` | `infrastructure.config.ApiKeyConfig` |
| `Sha256SecretHasher`, `SortableEncoder` | `infrastructure.keyformat` |
| `ApiKeyCreationProviderV1` + `ApiKeyCreationService` (`@Qualifier("creationV1")`) | `V1CredentialIssuer`; `IssueCredentialPort` bean picks the issuer named by `access.key-format.current` |
| `ApiKeyVerificationProviderV1` + `ApiKeyVerificationService` | `V1CredentialVerifier` (secret check only) behind the `CredentialVerifier` interface (`application.interfaces`); lookup, state and expiry checks live in `VerifyApiKeyHandler` and the aggregate |
| `ApiKeyToken` (mutable, `verified` flag) | immutable `ParsedApiKey` (internal) -> `VerifiedApiKey` |
| `ApiKeyDto` (nullable secret and hash) | `ApiKeyView` (no secrets) and `IssuedApiKeyResult` (secret, once) |
| `ApiKeyService`, `ApiKeyPersistenceService` | one `*UseCase` + `*Handler` per operation, each in its own `application.usecase.<name>` package |
| `ApiKeyController` in `web` | `presentation.api.controllers`, depend on the use cases only; DTOs in `presentation.api.dtos` |
| `ApiKeyInternalController` (`/internal/...` success/failure acks) | removed with the handshake; there is no internal HTTP surface |
| `ApiKeyFacadeImpl` | `presentation.facade.ApiKeyFacadeImpl`, implements `common.access.ApiKeyVerificationFacade` and calls `VerifyApiKeyUseCase` with a `VerifyApiKeyQuery` |
| `acknowledgeFailure` (user-facing) | removed; delete removes the key outright, so there is nothing to dismiss |
| schema `api_key`, tables `api_keys`, `api_key_next`, `outbox` | schema `access`, tables `api_keys`, `outbox` |
| Delivery Service (other context) | Dispatcher |

## HTTP status and errors
Responses carry one status field, `status`, a presentation enum (`ApiKeyStatus`) derived from the internal `ApiKeyState` and the clock: `ACTIVE` or `EXPIRED` (a deleted key is not returned at all: it is not found). It says where the key stands, never why: no revisions, version counters or secret hashes are exposed, and the internal states can change without breaking clients.

**Errors.** Every error is an RFC 9457 `application/problem+json` body with a human-readable `detail` that is safe to show to end users and a machine-readable `code` that names the exact cause, so a client can branch on it without reading the message. Nothing the caller sent and nothing the server threw is echoed back.

Request validation (`400`, top-level `code` `VALIDATION_FAILED`) lists every bad field at once as `errors: [{field, code, message}]`. Each constraint's message is a key in `ValidationMessages.properties`: the key is the field error's `code`, the text its `message`. To add a rule, add a key and use `message = "{KEY}"` on the constraint.

| Field error `code` | Field | When |
|---|---|---|
| `NAME_REQUIRED` | `name` | Create: missing or blank. |
| `NAME_BLANK` | `name` | Update: given but blank. |
| `NAME_TOO_LONG` | `name` | More than 256 characters. |
| `NAME_INVALID_CHARACTERS` | `name` | Contains a control character (newline, tab, escape). |
| `EXPIRY_REQUIRED` | `expiry` | Create: missing. |
| `PERMISSION_LEVEL_REQUIRED` | `permissions[SCOPE]` | Create: a level is `null`. (On update a `null` level means "leave unchanged".) |
| `PERMISSION_SCOPE_REQUIRED` | `permissions` | A scope key is null. |

Every other error has one top-level code:

| Code | Status | When |
|---|---|---|
| `REQUEST_BODY_MISSING` | 400 | The body is required but absent or empty. |
| `REQUEST_BODY_NOT_JSON` | 400 | The body is not valid JSON. |
| `REQUEST_BODY_WRONG_TYPE` | 400 | Valid JSON of the wrong shape, such as an array where an object is expected. |
| `REQUEST_BODY_INVALID` | 400 | Any other unreadable body. |
| `INVALID_LIFETIME` | 400 | `expiry` is not `ONE_DAY`, `ONE_MONTH` or `NEVER`. Carries `field`. |
| `INVALID_SCOPE` | 400 | A key in `permissions` is not a known scope. Carries `field`. |
| `INVALID_ACCESS_LEVEL` | 400 | A level in `permissions` is not a known level. Carries `field`. |
| `INVALID_<PROPERTY>` | 400 | Any other body property of the wrong type, named after it. Carries `field`. |
| `INVALID_SCOPE_LEVEL` | 400 | The level is known but that scope does not accept it. |
| `INVALID_USER_ID`, `INVALID_API_KEY_ID` | 400 | A path variable cannot be converted (not a UUID). Carries `parameter`, never the value. |
| `API_KEY_NOT_FOUND` | 404 | No such key, or not the caller's. |
| `ROUTE_NOT_FOUND` | 404 | No such endpoint. |
| `METHOD_NOT_ALLOWED` | 405 | Known route, wrong method; the `Allow` header is kept. |
| `NOT_ACCEPTABLE` | 406 | The `Accept` header asks for something the API cannot produce. |
| `CONCURRENT_MODIFICATION` | 409 | Concurrent changes kept winning the race and the retries ran out. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Missing or wrong `Content-Type`. |
| `INTERNAL_ERROR` | 500 | Anything unexpected: generic message, logged in full on the server. |

Verification failures (`MALFORMED_CREDENTIALS`, `INVALID_CREDENTIALS`, `KEY_EXPIRED`) use the same body. Both handlers live in Access: `presentation/api/errors/WebExceptionHandler` (framework and unexpected errors) and `presentation/api/controllers/ApiKeyExceptionHandler`, which handles the Access domain exceptions and runs first. Every context owns its error handling; there is no shared error package. Domain refusals carry their code in the exception (`UpdateFailureCode`, and the common `VerificationFailureCode`), so the code is chosen where the rule is, not guessed from a message.


## Known bugs fixed by the refactor

1. The verification provider caught `NoSuchElementException`, but the lookup threw `ApiKeyNotFoundException`.
2. Acks (confirm/reject) carried no revision, so a late ack could apply to the wrong change. The whole handshake is gone now (see "HQ is the source of truth" above).

## Outbox

Projection events are registered by the `ApiKey` aggregate itself (`issue`, `update`, `delete`) as domain events and collected with `pullDomainEvents()`. `ApiKeysAdapter` maps each one through `ApiKeyProjectionMapper.toProjectionCommand(...)` and writes the resulting command to the outbox itself (`EventOutboxEntity.of(command)`) right after `save`/`delete`, in the caller's transaction, so if the row cannot be written the key change (or the delete) rolls back too. Event types are `apiKey.projection.create`, `apiKey.projection.update` and `apiKey.projection.delete`.

The domain's `ApiKeyEvent` is a `sealed interface` permitting exactly `ApiKeyCreated`, `ApiKeyUpdated` and `ApiKeyDeleted`; the mapper's exhaustive `switch` stops compiling when a type is added. The published `ApiKeyProjectionCommand` is also sealed and permits exactly the three common command records, and `EventOutboxEntity.of(...)` switches over them.

### Event timestamps and fields

| Field | Meaning |
|---|---|
| `occurredAt` | When the change reached the outbox. Set by the aggregate from the command's `now`. Never null. Outbox column `occurred_at NOT NULL`. |
| `emittedAt` | When the event was published into the stream. Neither the aggregate's domain events nor the mapper know this, so the mapped command carries `null`, and so does the outbox row until a relay publishes it. Outbox column `emitted_at` is nullable. |
| `createdAt` | When the key was issued. Unchanged. Outbox column `created_at NOT NULL`. |
| `expiresAt` | When the key stops being valid; null means never. It restates the key's own expiry as of this revision (the event timestamp is `occurredAt`, not this), so the data-plane projection knows the key's lifetime. Outbox column `expires_at` is nullable. |

Key expiry **is propagated**: every projection command carries the key's `expiresAt` alongside `createdAt`, and the outbox stores it (nullable). The data plane can use it to display or refuse expiry; enforcement inside Access still happens in `canAuthenticate(now)`, whatever the data plane decides.

## Permissions: scopes and levels

`Permissions` maps `Scope` to `AccessLevel`. Absent means `NONE`; consumers must also treat a scope they do not know as `NONE`.

| Scope | Allowed levels |
|---|---|
| `INGESTION` | `NONE`, `WRITE` |
| `TENANT` | `NONE`, `READ`, `WRITE`, `READ_WRITE` |
| `CHANNEL` | `NONE`, `READ`, `WRITE`, `READ_WRITE` |

**Adding a scope** (for example `MONITORING`) is one new constant in `access/domain/aggregates/apikey/Scope.java`, listing the levels it accepts beyond `NONE`:

```java
MONITORING(AccessLevel.READ)
```

Nothing else changes: updates (`PermissionChanges`), persistence, the web layer, the events and the validation all work from the enum. `ScopeTest` runs over `Scope.values()`, so a badly declared scope fails the build. Existing keys simply have `NONE` for it.

**Wire shapes** (scope and level names are the values; renaming one is a contract change):
- REST request/response `permissions` is a JSON object keyed by scope name. Create and update accept a partial object; responses list every scope, with `NONE` where nothing was granted. A level the scope does not accept, or an unknown scope or level name, is HTTP 400.
- Events (`CreateApiKeyProjectionCommand`, `UpdateApiKeyProjectionCommand`, `DeleteApiKeyProjectionCommand`) carry `permissions` as a plain `Map<String, String>` listing only granted scopes, for example `{"INGESTION":"WRITE","TENANT":"READ"}`; `{}` means no access. `ApiKeyProjectionMapper.toWirePermissions` builds it in scope-declaration order and makes it unmodifiable. The outbox stores exactly that JSON object in `access.outbox.permissions`.

**Storage:** `access.api_key_permissions` holds one row per granted scope (`scope`, `access_level`); the outbox keeps the JSON text. `JpaMappingTest` checks these mappings offline; they have not run against a real Postgres.

## Class structure
### How to read the code

1. Start at **`access/domain/aggregates/apikey/ApiKey.java`**. It is the whole business: a key is issued, changed and deleted through its methods, and every accepted change is applied at once. It has no setters.
   Its siblings are the small value types; `domain/exceptions` holds its exceptions and
   `domain/repositories/ApiKeys` is the storage contract the domain defines.
2. `access/application/usecase` has one package per operation (`issue`, `get`, `update`, `delete`,
   `verify`). Each holds the `*UseCase` interface that the outside world calls, its
   `*Command` or `*Query` (and result record), and its package-private `*Handler`. A handler loads the key, calls
   one method on it, saves it. It holds no business rules.
3. `access/application/model/ApiKeyView` is the secret-free read model several use cases return.
   `access/application/transaction` holds `RetriedTransaction` and `OptimisticLockRetry`, which every writing
   handler shares.
4. `access/application/interfaces` lists what Access needs from outside besides storage:
   `IssueCredentialPort` (make a new credential) and `CredentialVerifier` (check a secret).
5. `access/presentation` is how the outside reaches Access: `api/controllers` (REST controllers), `api/dtos`
   (request and response bodies), `api/errors` (the problem-body handlers: the domain handler runs first, the
   framework handler is the last line of defence) and `facade` (in-process verify).
6. `access/infrastructure` holds the Spring/JPA/crypto code that implements what the application and domain need:
   `persistence` (`models` for the outbox entity, `repositories` for the database access and outbox writes),
   `keyformat` (credential format V1) and `config` (wiring).
7. `common/access` is the published contract other contexts may use: the outgoing projection commands
   (`events/outbound`), and the verification facade with
   its query, view, sealed result and failure code (`ApiKeyVerificationFacade`, `VerifyApiKeyQuery`, `ApiKeyView`,
   `ApiKeyVerifyResult` with `Success`/`Failure`, `VerificationFailureCode`). It is domain-free: permissions travel as
   a plain `Map<String, String>` of scope name to level name. It has no Spring, JPA or JSON dependencies and never imports `access`.
8. `access/domain/aggregates/apikey/events` holds the domain's own events (`ApiKeyEvent` sealed, `ApiKeyCreated`,
   `ApiKeyUpdated`, `ApiKeyDeleted`). The aggregate registers them; `access/application/events/ApiKeyProjectionMapper`
   is the single place that translates them into the `common` projection commands.

**Dependency rule:** `presentation` and `infrastructure` -> `application` -> `domain`; every access layer may depend
on `common`. `common` depends only on the JDK and Lombok and never on `access`; `domain` depends only on itself, the
JDK, `jakarta.persistence` (the aggregate is a JPA entity), Lombok, the UUID generator and JSpecify. The domain and
`common` do not know each other: `ApiKeyProjectionMapper` in `application` is the seam. Nothing in `access` depends on
`presentation` or `infrastructure`, and those two never know each other. `ArchitectureTest` fails the build when a
layer points the wrong way.

### 1. Layers and allowed dependencies

```mermaid
flowchart TB
    presentation["presentation (api/controllers, api/dtos, api/errors, facade)"]
    infrastructure["infrastructure (persistence, keyformat, config)"]
    application["application (usecase, model, transaction, interfaces)"]
    domain["domain (aggregates/apikey, events, exceptions, repositories)"]
    common["common/access (events, facade, query, result, failures)"]

    presentation --> application
    presentation --> domain
    presentation --> common
    infrastructure --> application
    infrastructure --> domain
    infrastructure --> common
    application --> domain
    application --> common
    domain -.->|no dependency| common
```

An arrow means "may depend on". `application` never depends on `presentation` or `infrastructure`; those two never
depend on each other. `common` is framework-free and domain-free: it is plain records, enums and one interface. The
domain owns its events and never imports `common`; the application's `ApiKeyProjectionMapper` translates domain events
into the published commands and permission maps. The repository contract (`domain/repositories/ApiKeys`) lives in the
domain, so `application` and `infrastructure` both depend on it and nothing points back at them. `presentation` may
not use the repository contract or `application/interfaces` directly; it talks to the use cases.

### 2. Domain (`access/domain`)

The aggregate and its value types are in `domain/aggregates/apikey`, its events in
`domain/aggregates/apikey/events`, the exceptions in `domain/exceptions`, the repository contract in
`domain/repositories`. `Permissions`, `Scope` and `AccessLevel` are domain types too.

```mermaid
classDiagram
    class ApiKey {
        <<aggregate root>>
        apiKeyId
        userId
        accessId
        accessSecretHash
        apiKeyName
        createdAt
        expiresAt
        revision
        lockVersion
        +issue(userId, name, credential, permissions, createdAt, expiresAt) ApiKey
        +update(changes, now) boolean
        +delete(now)
        +state(now) ApiKeyState
        +canAuthenticate(now) boolean
        +getPermissions() Permissions
        +isOwnedBy(userId) boolean
        +pullDomainEvents() List
    }
    class ApiKeyEvent {
        <<sealed interface, domain event>>
        apiKeyId
        version
        occurredAt
        userId
        accessId
        apiKeyName
        permissions
        createdAt
        expiresAt
    }
    class ApiKeyCreated {
        <<record, domain event>>
        accessSecretHash
    }
    class ApiKeyUpdated {
        <<record, domain event>>
    }
    class ApiKeyDeleted {
        <<record, domain event>>
    }
    class ApiKeyState {
        <<enumeration>>
        ACTIVE
        EXPIRED
    }
    class Lifetime {
        <<enumeration>>
        ONE_DAY
        ONE_MONTH
        NEVER
        +expiryFrom(now) Instant
    }
    class ApiKeyChanges {
        <<record>>
        name
        lifetime
        permissions
    }
    class PermissionChanges {
        <<record>>
        levels
        +applyTo(current) Permissions
    }
    class IssuedCredential {
        <<record>>
        accessId
        accessSecret
        secretHash
    }
    class ApiKeyNotFoundException
    class ApiKeyUpdateFailure
    class ApiKeys {
        <<repository interface>>
    }

    ApiKey "1" *-- "0..*" ApiKeyEvent : pendingEvents
    ApiKeyEvent <|.. ApiKeyCreated
    ApiKeyEvent <|.. ApiKeyUpdated
    ApiKeyEvent <|.. ApiKeyDeleted
    ApiKeys ..> ApiKey : loads and stores
    ApiKey ..> ApiKeyState : state
    ApiKey ..> ApiKeyChanges : update
    ApiKey ..> IssuedCredential : issue
    ApiKeyChanges --> Lifetime
    ApiKeyChanges --> PermissionChanges
    ApiKey ..> ApiKeyNotFoundException : throws
    ApiKey ..> ApiKeyUpdateFailure : throws
```

How the pieces fit: the plain fields (`apiKeyName`, permissions, `expiresAt`) are the key's current state; a change
is applied to them the moment its command is accepted. `state(now)` is derived from the expiry and the clock; it is
never stored. `revision` is the change counter carried as `version` in events. `ApiKeyNotFoundException`,
`ApiKeyUpdateFailure` and `InvalidPermissionsException` are plain runtime exceptions.

### 3. Published contracts (`common/access`) and the mapper

`common/access` holds plain, domain-free records, a sealed verification result and one interface. Permissions cross
the boundary as a `Map<String, String>` (scope name to level name), so `common` never imports `domain`; the domain
owns its own events and never imports `common`. `ApiKeyProjectionMapper` (in `access/application/events`) is the only
place the domain model and the published contract meet.

```mermaid
classDiagram
    class ApiKeyProjectionCommand {
        <<sealed interface, common>>
        eventType
        version
        occurredAt
        emittedAt
        apiKeyName
        apiKeyId
        userId
        accessId
        permissions Map~String, String~
        createdAt
    }
    class CreateApiKeyProjectionCommand {
        <<record, common>>
        accessSecretHash
    }
    class UpdateApiKeyProjectionCommand {
        <<record, common>>
    }
    class DeleteApiKeyProjectionCommand {
        <<record, common>>
    }
    class ApiKeyVerificationFacade {
        <<interface, common>>
        +verifyApiKey(query) ApiKeyVerifyResult
    }
    class VerifyApiKeyQuery {
        <<record, common>>
        accessId
        accessSecret
    }
    class ApiKeyView {
        <<record, common>>
        apiKeyId
        userId
        apiKeyName
        permissions Map~String, String~
        createdAt
        expiresAt
    }
    class ApiKeyVerifyResult {
        <<sealed interface, common>>
    }
    class Success {
        <<record, common>>
        apiKeyView ApiKeyView
    }
    class Failure {
        <<record, common>>
        reason VerificationFailureCode
        message
    }
    class VerificationFailureCode {
        <<enumeration, common>>
        MALFORMED_CREDENTIALS
        INVALID_CREDENTIALS
        KEY_EXPIRED
    }
    class InvalidPermissionsException {
        <<exception, domain>>
    }

    ApiKeyProjectionCommand <|.. CreateApiKeyProjectionCommand
    ApiKeyProjectionCommand <|.. UpdateApiKeyProjectionCommand
    ApiKeyProjectionCommand <|.. DeleteApiKeyProjectionCommand
    ApiKeyVerificationFacade --> VerifyApiKeyQuery
    ApiKeyVerificationFacade --> ApiKeyVerifyResult
    ApiKeyVerifyResult <|.. Success
    ApiKeyVerifyResult <|.. Failure
    Success --> ApiKeyView
    Failure --> VerificationFailureCode
```

`ApiKeyProjectionCommand` is sealed: only the three records may implement it, and they are the only things Access
writes to the outbox. They carry the key's `createdAt` and `expiresAt`; `occurredAt` is when the change reached the
outbox, `emittedAt` is null until something publishes it. `permissions` is a scope-name to level-name map; the wire
names are `INGESTION`,
`TENANT`, `CHANNEL` and `NONE`, `READ`, `WRITE`, `READ_WRITE`. There is nothing inbound: the data plane conforms to
the published events; it does not answer.

`ApiKeyProjectionMapper.toProjectionCommand(ApiKeyEvent)` uses an exhaustive `switch` over the sealed domain event
type. `toWirePermissions(Permissions)` copies the domain's scope/level map into an ordered, unmodifiable
`Map<String, String>`. The mapper deliberately leaves `emittedAt` null: only the relay stamps it.

`ApiKeyVerificationFacade.verifyApiKey(...)` returns `ApiKeyVerifyResult`, and never throws for a rejected credential.
`Success` carries the secret-free `ApiKeyView`; `Failure` carries a machine-readable `VerificationFailureCode` and an
end-user-safe message, so callers branch on the result instead of catching an Access exception. Access internally
reports a rejection by throwing `ApiKeyVerificationFailure` from
`access.application.usecase.service.verify`; `ApiKeyFacadeImpl` catches it and returns `Failure`. The test-only
`VerifyProbeController` translates a `Failure` back into that internal exception so the HTTP 400/401 mapping stays
covered. `InvalidPermissionsException` stays a domain exception.

### 4. Application layer (`access/application`)

Each row of the diagram is one operation: HTTP or facade -> use case -> handler -> the repository (a domain contract) and the credential interfaces.

```mermaid
flowchart LR
    subgraph portin["usecase/* (UseCase interfaces)"]
        IssueUC["IssueApiKeyUseCase"]
        GetUC["GetApiKeyUseCase"]
        UpdateUC["UpdateApiKeyUseCase"]
        DeleteUC["DeleteApiKeyUseCase"]
        VerifyUC["VerifyApiKeyUseCase"]
    end
    subgraph usecase["usecase/* (package-private handlers) and transaction"]
        IssueH["IssueApiKeyHandler"]
        GetH["GetApiKeyHandler"]
        UpdateH["UpdateApiKeyHandler"]
        DeleteH["DeleteApiKeyHandler"]
        VerifyH["VerifyApiKeyHandler"]
        Tx["RetriedTransaction"]
        Retry["OptimisticLockRetry"]
    end
    Parsed["usecase/service/verify: ParsedApiKey"]
    subgraph portout["domain/repositories and application/interfaces"]
        ApiKeysP["ApiKeys"]
        IssueCredP["IssueCredentialPort"]
        VerifierP["CredentialVerifier"]
    end

    IssueUC --> IssueH
    GetUC --> GetH
    UpdateUC --> UpdateH
    DeleteUC --> DeleteH
    VerifyUC --> VerifyH

    IssueH --> ApiKeysP
    IssueH --> IssueCredP
    GetH --> ApiKeysP
    UpdateH --> ApiKeysP
    UpdateH --> Tx
    DeleteH --> Tx
    Tx --> Retry
    DeleteH --> ApiKeysP
    VerifyH --> ApiKeysP
    VerifyH --> VerifierP
    VerifyH --> Parsed
```

Inputs and outputs of each use case (all records, each in its own `usecase/user/<name>` or `usecase/service/<name>` package except `ApiKeyView` in `application/model` and `VerifyApiKeyQuery` in `common/access`):

| Use case | Takes | Returns |
|---|---|---|
| `IssueApiKeyUseCase` | `IssueApiKeyCommand(userId, name, lifetime, permissions)` | `IssuedApiKeyResult(key, accessSecret)`: the only place the secret appears |
| `GetApiKeyUseCase` | `GetApiKeyQuery(userId, apiKeyId)` | `ApiKeyView` (no secrets, the HTTP layer turns its `state` into the public `ApiKeyStatus`) |
| `UpdateApiKeyUseCase` | `UpdateApiKeyCommand(userId, apiKeyId, name, lifetime, permissions)` | `ApiKeyView` |
| `DeleteApiKeyUseCase` | `DeleteApiKeyCommand(userId, apiKeyId)` | nothing |
| `VerifyApiKeyUseCase` | `VerifyApiKeyQuery(accessId, accessSecret)` (in `common/access`) | `VerifiedApiKey` |

Notes: `GetApiKeyHandler` and `VerifyApiKeyHandler` run in read-only transactions and `IssueApiKeyHandler` is
`@Transactional`. Every handler that changes an existing key (Update, Delete)
goes through `RetriedTransaction`: a fresh `REQUIRES_NEW` transaction per attempt, retried through
`OptimisticLockRetry` when another writer changed the key first. Each retry re-reads the key, so the domain rules
decide the outcome against its current state. When retries run out the caller gets `ApiKeyUpdateFailure` (409).
`ParsedApiKey` (in `usecase/service/verify`) is an internal record that splits `V1_xxx` into access ID, secret and key format.
`RetriedTransaction` and `OptimisticLockRetry` are public classes in `application/transaction` because handlers in
several packages use them; the handlers themselves stay package-private.

### 5. Presentation and infrastructure (`access/presentation`, `access/infrastructure`)

```mermaid
flowchart LR
    subgraph adapterin["presentation (api/controllers, facade)"]
        Ctl["ApiKeyController (user REST)"]
        Adv["ApiKeyExceptionHandler"]
        FacadeImpl["ApiKeyFacadeImpl"]
    end
    subgraph portin["application/usecase/*"]
        UCs["the 5 use case interfaces"]
    end
    subgraph portout["domain/repositories, application/interfaces"]
        ApiKeysP["ApiKeys"]
        IssueCredP["IssueCredentialPort"]
        VerifierP["CredentialVerifier"]
    end
    subgraph adapterout["infrastructure"]
        ApiKeysAdapter["persistence/repositories: ApiKeysAdapter"]
        JpaRepo["persistence/repositories: JpaApiKeyRepository"]
        OutboxRepo["persistence/repositories: EventOutboxRepository"]
        Issuer["keyformat/v1: V1CredentialIssuer"]
        V1Verifier["keyformat/v1: V1CredentialVerifier"]
    end
    Facade["common: ApiKeyVerificationFacade"]
    Cfg["config: ApiKeyConfig"]

    Ctl --> UCs
    FacadeImpl --> UCs
    FacadeImpl -.->|implements| Facade
    ApiKeysAdapter -.->|implements| ApiKeysP
    Issuer -.->|picked by config as| IssueCredP
    V1Verifier -.->|implements| VerifierP
    ApiKeysAdapter --> JpaRepo
    ApiKeysAdapter -->|maps domain events and writes projection commands| OutboxRepo
    Cfg -->|exposes current CredentialIssuer as| IssueCredP
```

`V1CredentialIssuer` implements `CredentialIssuer` (infrastructure-local interface), not `IssueCredentialPort` directly:
`ApiKeyConfig` picks the issuer whose `keyFormat()` equals `access.key-format.current` (default `V1`) and exposes
it as the single `IssueCredentialPort` bean. `ApiKeyFacadeImpl` implements the `common` `ApiKeyVerificationFacade`
and takes a `VerifyApiKeyQuery`. `ApiKeyExceptionHandler` (in `presentation/api/controllers`) maps exceptions to HTTP: not found 404,
update failure 409, verification failure 401, parsing failure and invalid permissions 400.

### 6. Sequence: update an API key

```mermaid
sequenceDiagram
    participant Client
    participant Ctl as ApiKeyController
    participant H as UpdateApiKeyHandler
    participant Key as ApiKey
    participant Map as ApiKeyProjectionMapper
    participant Ad as ApiKeysAdapter
    participant DB as PostgreSQL

    Client->>Ctl: PATCH /api/v1/user/userId/apikey/apiKeyId
    Ctl->>H: handle(UpdateApiKeyCommand)
    Note over H: RetriedTransaction: OptimisticLockRetry plus a REQUIRES_NEW transaction per attempt
    H->>Ad: requireOwned(userId, apiKeyId)
    Ad->>DB: select key
    DB-->>H: ApiKey
    H->>Key: update(changes, now)
    Note over Key: revision +1, attributes applied, registers ApiKeyUpdated
    Key-->>H: true (false means nothing changed, stop here)
    H->>Ad: save(key)
    Ad->>DB: save key
    Ad->>Key: pullDomainEvents()
    Ad->>Map: toProjectionCommand(ApiKeyUpdated)
    Map-->>Ad: UpdateApiKeyProjectionCommand with a wire permissions map
    Ad->>DB: insert outbox row (same transaction)
    DB-->>H: commit (a failing insert rolls the key change back)
    H-->>Ctl: ApiKeyView
    Ctl-->>Client: 200 with GetResponseDto
```

### 7. Sequence: verify an API key

```mermaid
sequenceDiagram
    participant Caller
    participant F as ApiKeyFacadeImpl
    participant H as VerifyApiKeyHandler
    participant V as CredentialVerifier
    participant Ad as ApiKeysAdapter
    participant Key as ApiKey

    Caller->>F: verifyApiKey(VerifyApiKeyQuery)
    F->>H: handle(VerifyApiKeyQuery)
    H->>H: ParsedApiKey.parse (bad format: MALFORMED_CREDENTIALS)
    H->>V: find verifier that supports the key format
    H->>Ad: findByAccessId(accessId)
    Ad-->>H: ApiKey (missing: INVALID_CREDENTIALS)
    H->>V: matches(presentedSecret, storedHash)
    V-->>H: false means INVALID_CREDENTIALS
    H->>Key: canAuthenticate(now)
    Key-->>H: false for an expired key: KEY_EXPIRED
    H-->>F: VerifiedApiKey
    F-->>Caller: Success(new ApiKeyView(...))
    Note over H,F: On a rejection the handler throws ApiKeyVerificationFailure; the facade catches it and returns Failure(code, message)
```

A rejected verification is delivered as `ApiKeyVerifyResult.Failure` with a machine-readable `reason` and a message
written for end users, so a gateway can branch on the code and pass the message on. It is never an exception across the
facade boundary. An unknown access id, a wrong secret and an unsupported key format are one outcome
(`INVALID_CREDENTIALS`), and the secret is checked before the key's state, so `KEY_EXPIRED` is only ever revealed to
a caller who holds the right secret. A deleted key is simply unknown, so it fails with `INVALID_CREDENTIALS`. Nothing
leaks about keys they do not hold.

| Code | Message | HTTP (if mapped) |
|---|---|---|
| `MALFORMED_CREDENTIALS` | The API key is missing or not in a recognized format. | 400 |
| `INVALID_CREDENTIALS` | The API key or secret is invalid. | 401 |
| `KEY_EXPIRED` | The API key has expired. | 401 |

### 8. Package tree (every class)

```
me.dhiren9939.backend
├── BackendApplication                      Spring Boot entry point
├── common/access/                         published contracts (domain-free, no framework dependencies)
│   ├── ApiKeyVerificationFacade            in-process "verify this credential" entry point
│   ├── VerifyApiKeyQuery                   access ID plus presented secret (toString masks the secret)
│   ├── ApiKeyVerifyResult                  sealed Success(ApiKeyView) or Failure(VerificationFailureCode, message)
│   ├── ApiKeyView                          secret-free payload carried by Success
│   ├── VerificationFailureCode             MALFORMED_CREDENTIALS, INVALID_CREDENTIALS, KEY_EXPIRED
│   └── events/outbound/                    what Access sends to the data plane (the outbox)
│       ├── ApiKeyProjectionCommand         sealed interface of the three projection commands
│       ├── CreateApiKeyProjectionCommand   a new key (carries the secret hash)
│       ├── UpdateApiKeyProjectionCommand   a changed key
│       └── DeleteApiKeyProjectionCommand   a deleted key
└── access/
    ├── domain/
    │   ├── aggregates/apikey/
    │   │   ├── ApiKey                      the aggregate: all lifecycle rules, JPA entity
    │   │   ├── ApiKeyState                 derived lifecycle state (ACTIVE, EXPIRED)
    │   │   ├── Lifetime                    ONE_DAY, ONE_MONTH, NEVER and expiryFrom(now)
    │   │   ├── ApiKeyChanges               optional name, lifetime, permission changes
    │   │   ├── PermissionChanges           partial scope-to-level update
    │   │   ├── IssuedCredential            access ID, one-time secret, secret hash
    │   │   ├── Permissions                 immutable scope-to-level map; missing scope = NONE
    │   │   ├── Scope                       INGESTION, TENANT, CHANNEL and the levels each accepts
    │   │   ├── AccessLevel                 NONE, READ, WRITE, READ_WRITE
    │   │   └── events/                     ApiKeyEvent (sealed), ApiKeyCreated, ApiKeyUpdated, ApiKeyDeleted
    │   ├── exceptions/                     ApiKeyNotFoundException, ApiKeyUpdateFailure, InvalidPermissionsException
    │   └── repositories/ApiKeys            storage contract for the aggregate
    ├── application/
    │   ├── usecase/                        user/ and service/, one package per operation, each with its *UseCase, *Command or *Query
    │   │   │                               and package-private *Handler:
    │   │   ├── issue/                      IssueApiKeyUseCase/Command/Handler, IssuedApiKeyResult
    │   │   ├── get/                        GetApiKeyUseCase/Query/Handler
    │   │   ├── update/                     UpdateApiKeyUseCase/Command/Handler
    │   │   ├── delete/                     DeleteApiKeyUseCase/Command/Handler
    │   │   └── verify/                     VerifyApiKeyUseCase/Handler, VerifiedApiKey, ParsedApiKey, ApiKeyVerificationFailure (query comes from common/access)
    │   ├── events/ApiKeyProjectionMapper   domain events and permissions -> the common projection contract
    │   ├── model/                          ApiKeyView (CRUD read model; the verification view is common/access.ApiKeyView)
    │   ├── transaction/                    RetriedTransaction, OptimisticLockRetry
    │   └── interfaces/                     IssueCredentialPort, CredentialVerifier
    ├── presentation/
    │   ├── api/controllers/                ApiKeyController, ApiKeyExceptionHandler
    │   ├── api/errors/                     WebExceptionHandler (framework errors), ApiProblems
    │   ├── api/dtos/                       CreateRequestDto, UpdateRequestDto,
    │   │                                   CreateResponseDto, GetResponseDto, GetPageResponseDto (unused), ApiKeyStatus
    │   └── facade/ApiKeyFacadeImpl         implements ApiKeyVerificationFacade via VerifyApiKeyUseCase
    └── infrastructure/
        ├── persistence/
        │   ├── models/EventOutboxEntity    the outbox row and its mapping from events
        │   └── repositories/               ApiKeysAdapter, JpaApiKeyRepository, EventOutboxRepository
        ├── keyformat/                      CredentialIssuer, Sha256SecretHasher, SortableEncoder,
        │                                   v1/V1CredentialIssuer, v1/V1CredentialVerifier
        └── config/ApiKeyConfig             Clock, SecureRandom, selects the current key format
```

Database objects (schema `access`): `api_keys`, `api_key_permissions`, `outbox`. The hand-written reference SQL is
`backend/src/main/resources/db.migration/access/V1__create_access_schema.sql` (schema creation),
`V2__drop_pending_changes.sql` (drops the pending-change tables and columns the old confirm/reject handshake used) and
`V3__remove_api_key_tombstones.sql` (drops `deleted_at`; deletes remove the row outright).

### 9. Where do I change X?

| I want to... | Change |
|---|---|
| Add a permission scope (e.g. `MONITORING`) | One constant in `domain/aggregates/apikey/Scope` with its allowed levels. Permissions, updates, storage, web and event payloads all work from the enum. `ScopeTest` checks the new constant automatically. |
| Add an event type | New record in `domain/aggregates/apikey/events`, add it to `ApiKeyEvent` `permits`, register it in `ApiKey`, add a branch to `ApiKeyProjectionMapper`, then add the matching `common/access/events/outbound` record and `EventOutboxEntity.of(...)` branch (the compiler forces both switches). Update `ApiKeyProjectionMapperTest`, `ApiKeyProjectionCommandContractTest` and the Access section of this document. |
| Add a use case | A new package `application/usecase/user/<name>` (the key owner asks for it) or `application/usecase/service/<name>` (another service asks for it) with the `*UseCase`, the command or query (and result) and a package-private `*Handler` (writing handlers go through `RetriedTransaction`), a method on `ApiKey` if there is a rule, a controller method, and a handler test in the same test package using `InMemoryApiKeys`. |
| Change the key format | Add `v2/V2CredentialIssuer` (implements `CredentialIssuer`) and `v2/V2CredentialVerifier` (implements `CredentialVerifier`), then set `access.key-format.current=V2`. Keep the V1 verifier so old keys still work. |
| Add or change an endpoint | A method in `presentation/api/controllers/ApiKeyController` mapping to a command, DTOs in `presentation/api/dtos`, errors in `ApiKeyExceptionHandler`. Tests: `ApiKeyControllerTest` and `ApiKeyHttpIntegrationTest`. |
| Change the database schema | Add a new `V<n>__*.sql` under `db.migration/access` (do not edit applied migrations) and keep the JPA mappings (`ApiKey`, `EventOutboxEntity` in `infrastructure/persistence/models`) in step with what the migrations produce. The app itself uses `ddl-auto=update`; `SqlMigrationValidateIntegrationTest` fails if the SQL files and mappings disagree. |
| Change a business rule | `ApiKey` (or `PermissionChanges` / `Permissions`). Handlers should not need to change. |

## Class hierarchy diagrams
### Layers

```mermaid
flowchart TB
    presentation["presentation<br/>api/controllers, api/dtos, api/errors, facade"]
    infrastructure["infrastructure<br/>persistence, keyformat, config"]
    application["application<br/>usecase, events, model, transaction, interfaces"]
    domain["domain<br/>aggregates/apikey, events, exceptions, repositories"]
    common["common/access<br/>events, verification contract"]

    presentation --> application
    presentation --> domain
    presentation --> common
    infrastructure --> application
    infrastructure --> domain
    infrastructure --> common
    application --> domain
    application --> common
    domain -. "must not depend" .-> common
```

The only bridge between `domain` and `common` is `application.events.ApiKeyProjectionMapper`. `common` imports no
`access` package, so other contexts can publish and consume the contract without seeing the aggregate.

### Domain (`access.domain`)

```mermaid
classDiagram
    direction TB

    class ApiKey {
        +issue(...) ApiKey
        +update(ApiKeyChanges, Instant) boolean
        +delete(Instant) void
        +canAuthenticate(Instant) boolean
        +state(Instant) ApiKeyState
        +pullDomainEvents() List~ApiKeyEvent~
    }
    class Permissions
    class PermissionChanges
    class ApiKeyChanges
    class IssuedCredential
    class Lifetime {
        <<enumeration>>
    }
    class ApiKeyState {
        <<enumeration>>
    }
    class Scope {
        <<enumeration>>
    }
    class AccessLevel {
        <<enumeration>>
    }

    class ApiKeyEvent {
        <<sealed interface>>
    }
    class ApiKeyCreated
    class ApiKeyUpdated
    class ApiKeyDeleted

    class ApiKeys {
        <<interface>>
    }
    class ApiKeyNotFoundException
    class ApiKeyUpdateFailure
    class InvalidPermissionsException
    class UpdateFailureCode {
        <<enumeration>>
    }

    ApiKey ..> Permissions
    ApiKey ..> IssuedCredential
    ApiKey ..> Lifetime
    ApiKey ..> ApiKeyState
    ApiKey ..> ApiKeyEvent
    ApiKey ..> ApiKeyUpdateFailure
    ApiKey ..> UpdateFailureCode

    Permissions o-- Scope
    Permissions o-- AccessLevel
    PermissionChanges --> Scope
    PermissionChanges --> AccessLevel
    ApiKeyChanges --> Lifetime
    ApiKeyChanges --> PermissionChanges

    ApiKeyEvent <|.. ApiKeyCreated : permits
    ApiKeyEvent <|.. ApiKeyUpdated : permits
    ApiKeyEvent <|.. ApiKeyDeleted : permits

    ApiKeys ..> ApiKey : stores
    ApiKeyUpdateFailure --> UpdateFailureCode
    InvalidPermissionsException ..> Scope
```

The aggregate is also the JPA entity (`@Entity`, schema `access`). Permissions are `@ElementCollection` rows keyed
by `Scope`. `pendingEvents` is `@Transient` and is drained once by `pullDomainEvents()`.

### Application (`access.application`)

#### Use-case interfaces and handlers

| Operation | Interface | Handler | Input | Output |
|---|---|---|---|---|
| Issue | `IssueApiKeyUseCase` | `IssueApiKeyHandler (package)` | `IssueApiKeyCommand` | `IssuedApiKeyResult` |
| Get | `GetApiKeyUseCase` | `GetApiKeyHandler (package)` | `GetApiKeyQuery` | `ApiKeyView` |
| Update | `UpdateApiKeyUseCase` | `UpdateApiKeyHandler (package)` | `UpdateApiKeyCommand` | `ApiKeyView` |
| Delete | `DeleteApiKeyUseCase` | `DeleteApiKeyHandler (package)` | `DeleteApiKeyCommand` | none |
| Verify | `VerifyApiKeyUseCase` | `VerifyApiKeyHandler (package)` | `VerifyApiKeyQuery` | `VerifiedApiKey` |

`VerifyApiKeyUseCase` uses the `common` query directly; the user-facing commands and queries are Access application types.

```mermaid
classDiagram
    direction LR

    class IssueApiKeyUseCase { <<interface>> }
    class GetApiKeyUseCase { <<interface>> }
    class UpdateApiKeyUseCase { <<interface>> }
    class DeleteApiKeyUseCase { <<interface>> }
    class VerifyApiKeyUseCase { <<interface>> }

    class IssueApiKeyHandler
    class GetApiKeyHandler
    class UpdateApiKeyHandler
    class DeleteApiKeyHandler
    class VerifyApiKeyHandler

    class IssueApiKeyCommand
    class GetApiKeyQuery
    class UpdateApiKeyCommand
    class DeleteApiKeyCommand
    class IssuedApiKeyResult
    class ApiKeyView
    class ParsedApiKey
    class VerifiedApiKey

    class ApiKeys { <<interface>> }
    class RetriedTransaction
    class OptimisticLockRetry
    class IssueCredentialPort { <<interface>> }
    class CredentialVerifier { <<interface>> }

    class ApiKeyProjectionMapper {
        +toProjectionCommand(ApiKeyEvent) ApiKeyProjectionCommand
        +toWirePermissions(Permissions) Map~String,String~
    }
    class ApiKeyEvent { <<domain event>> }
    class ApiKeyProjectionCommand { <<common contract>> }

    IssueApiKeyHandler ..|> IssueApiKeyUseCase
    GetApiKeyHandler ..|> GetApiKeyUseCase
    UpdateApiKeyHandler ..|> UpdateApiKeyUseCase
    DeleteApiKeyHandler ..|> DeleteApiKeyUseCase
    VerifyApiKeyHandler ..|> VerifyApiKeyUseCase

    IssueApiKeyHandler ..> IssueApiKeyCommand
    IssueApiKeyHandler ..> IssuedApiKeyResult
    GetApiKeyHandler ..> GetApiKeyQuery
    GetApiKeyHandler ..> ApiKeyView
    UpdateApiKeyHandler ..> UpdateApiKeyCommand
    DeleteApiKeyHandler ..> DeleteApiKeyCommand
    VerifyApiKeyHandler ..> ParsedApiKey
    VerifyApiKeyHandler ..> VerifiedApiKey

    IssueApiKeyHandler --> ApiKeys
    GetApiKeyHandler --> ApiKeys
    UpdateApiKeyHandler --> ApiKeys
    DeleteApiKeyHandler --> ApiKeys
    VerifyApiKeyHandler --> ApiKeys

    IssueApiKeyHandler --> IssueCredentialPort
    VerifyApiKeyHandler --> CredentialVerifier
    UpdateApiKeyHandler --> RetriedTransaction
    DeleteApiKeyHandler --> RetriedTransaction
    RetriedTransaction --> OptimisticLockRetry

    ApiKeyProjectionMapper ..> ApiKeyEvent
    ApiKeyProjectionMapper ..> ApiKeyProjectionCommand
```

Every write handler follows the same shape: load through `ApiKeys`, call one aggregate method, save through
`ApiKeys`; `RetriedTransaction` owns the optimistic-lock retry. No handler contains a key rule.

### Presentation (`access.presentation`)

```mermaid
classDiagram
    direction TB

    class ApiKeyController {
        <<package-private>>
    }
    class ApiKeyExceptionHandler {
        <<package-private>>
    }
    class ApiKeyFacadeImpl {
        <<package-private>>
    }

    class IssueApiKeyUseCase { <<interface>> }
    class GetApiKeyUseCase { <<interface>> }
    class UpdateApiKeyUseCase { <<interface>> }
    class DeleteApiKeyUseCase { <<interface>> }
    class VerifyApiKeyUseCase { <<interface>> }

    class ApiKeyVerificationFacade { <<interface>> }
    class VerifyApiKeyQuery
    class ApiKeyVerifyResult
    class ApiKeyView

    ApiKeyController --> IssueApiKeyUseCase
    ApiKeyController --> GetApiKeyUseCase
    ApiKeyController --> UpdateApiKeyUseCase
    ApiKeyController --> DeleteApiKeyUseCase

    ApiKeyFacadeImpl ..|> ApiKeyVerificationFacade
    ApiKeyFacadeImpl --> VerifyApiKeyUseCase
    ApiKeyFacadeImpl ..> VerifyApiKeyQuery
    ApiKeyFacadeImpl ..> ApiKeyVerifyResult
    ApiKeyFacadeImpl ..> ApiKeyView
```

DTOs in `presentation/api/dtos`:

| Kind | Classes |
|---|---|
| Requests | `CreateRequestDto`, `UpdateRequestDto` |
| Responses | `CreateResponseDto`, `GetResponseDto`, `GetPageResponseDto` (unused), `ApiKeyStatus` |
| Shared | `RequestPatterns (package)` |

`ApiKeyExceptionHandler` maps `ApiKeyNotFoundException`, `ApiKeyUpdateFailure`, `InvalidPermissionsException` and
Access's internal `ApiKeyVerificationFailure` to HTTP responses (the last one is exercised through the test-only
probe). `ApiKeyFacadeImpl` catches that exception and returns `ApiKeyVerifyResult.Failure`. `ApiKeyController` talks
only to user use cases; `ApiKeyFacadeImpl` talks only to the service verify use case.

### Infrastructure (`access.infrastructure`)

```mermaid
classDiagram
    direction TB

    class ApiKeys { <<interface>> }
    class CredentialIssuer { <<interface>> }
    class CredentialVerifier { <<interface>> }
    class IssueCredentialPort { <<interface>> }
    class ApiKey { }
    class ApiKeyProjectionCommand { <<sealed interface>> }
    class ApiKeyProjectionMapper

    class ApiKeysAdapter {
        <<package-private>>
    }
    class JpaApiKeyRepository {
        <<interface>>
        <<package-private>>
    }
    class EventOutboxRepository {
        <<interface>>
        <<package-private>>
    }
    class EventOutboxEntity

    class V1CredentialIssuer {
        <<package-private>>
    }
    class V1CredentialVerifier {
        <<package-private>>
    }
    class Sha256SecretHasher
    class SortableEncoder
    class ApiKeyConfig {
        <<package-private>>
    }

    ApiKeysAdapter ..|> ApiKeys
    ApiKeysAdapter --> JpaApiKeyRepository
    ApiKeysAdapter --> EventOutboxRepository
    ApiKeysAdapter --> ApiKeyProjectionMapper
    ApiKeysAdapter --> ApiKey
    EventOutboxRepository ..> EventOutboxEntity
    EventOutboxEntity ..> ApiKeyProjectionCommand
    JpaApiKeyRepository ..> ApiKey

    V1CredentialIssuer ..|> CredentialIssuer
    V1CredentialVerifier ..|> CredentialVerifier
    V1CredentialIssuer --> SortableEncoder
    V1CredentialIssuer --> Sha256SecretHasher
    V1CredentialVerifier --> Sha256SecretHasher

    ApiKeyConfig ..> IssueCredentialPort
    ApiKeyConfig ..> CredentialIssuer
```

`ApiKeysAdapter` is the only production writer of the outbox. It maps the aggregate's drained domain events with
`ApiKeyProjectionMapper`, converts each command to an `EventOutboxEntity`, and saves it in the caller's transaction.
There is no outbox listener in this context.

### Published contract (`common.access`)

```mermaid
classDiagram
    direction TB

    class ApiKeyProjectionCommand { <<sealed interface>> }
    class CreateApiKeyProjectionCommand
    class UpdateApiKeyProjectionCommand
    class DeleteApiKeyProjectionCommand

    class ApiKeyVerificationFacade { <<interface>> }
    class VerifyApiKeyQuery
    class ApiKeyVerifyResult { <<sealed interface>> }
    class Success
    class Failure
    class ApiKeyView
    class VerificationFailureCode {
        <<enumeration>>
    }

    ApiKeyProjectionCommand <|.. CreateApiKeyProjectionCommand : permits
    ApiKeyProjectionCommand <|.. UpdateApiKeyProjectionCommand : permits
    ApiKeyProjectionCommand <|.. DeleteApiKeyProjectionCommand : permits

    ApiKeyVerificationFacade ..> VerifyApiKeyQuery
    ApiKeyVerificationFacade ..> ApiKeyVerifyResult
    ApiKeyVerifyResult <|.. Success : permits
    ApiKeyVerifyResult <|.. Failure : permits
    Success --> ApiKeyView
    Failure --> VerificationFailureCode
```

The outbound commands carry permissions as a `Map<String, String>` of scope name to level name, never a domain
type. There is nothing inbound: the data plane conforms to the published events.
`ApiKeyVerificationFacade` is implemented in `presentation.facade`; other contexts depend on the interface only. The
facade returns `Success(ApiKeyView)` or `Failure(VerificationFailureCode, message)` and never throws for a rejected
credential; the internal `ApiKeyVerificationFailure` lives under `access.application.usecase.service.verify`.

### Runtime path

```mermaid
flowchart LR
    user["User HTTP"] --> api["ApiKeyController"]
    api --> userUseCase["user use case"]
    userUseCase --> aggregate["ApiKey aggregate"]
    aggregate --> events["domain events"]
    events --> mapper["ApiKeyProjectionMapper"]
    mapper --> adapter["ApiKeysAdapter"]
    adapter --> outbox["access.outbox"]
    outbox -->|"read by the relay"| plane["Data plane conforms"]

    other["Other context"] --> facade["ApiKeyVerificationFacade"]
    facade --> verify["VerifyApiKeyHandler"]
    verify --> verifier["CredentialVerifier"]
```

The outbox row and the key change commit in one transaction. Verification is in-process; Access exposes no HTTP
verify endpoint.

## Tests
### Running them

Run from `backend/`. Numbers below were measured on this repo (Windows, Docker Desktop running).

| What | Command | Tests | Time |
|---|---|---|---|
| Unit tests only | `./mvnw test -Dgroups='!integration'` | 158 | about 30 s of Maven time |
| Integration tests only | `./mvnw test -Dgroups=integration` | 33 | about a minute (container start included; varies by machine) |
| Everything | `./mvnw test` | 191 | about 1.5 min |

- **Docker must be running for the integration tests.** They start a throwaway `postgres:17` container with
  Testcontainers (one container per test JVM, shared by all integration classes). They **never** use the
  `maindb` dev database from `docker-compose.yml`; a dev database on `localhost:5432` is not needed or touched.
- "Integration" means a class tagged `@Tag("integration")`. The tag sits on `PostgresIntegrationTest`
  (inherited by every subclass, including `BackendApplicationTests`) and on `SqlMigrationValidateIntegrationTest`.
  There is no special Maven profile; `-Dgroups` is the standard Surefire switch.
- Unit tests need no database, no Docker and no Spring application context. `ApiKeyConfigTest` builds
  `ApiKeyConfig` by hand, and `ApiKeyControllerTest` uses a standalone MockMvc setup.

### Where each kind of test lives

```
src/test/java/me/dhiren9939/backend
├── ArchitectureTest                                 layering rules (ArchUnit)
├── BackendApplicationTests                          Spring context loads on a real Postgres
├── common/access/                                   Permissions and Scope tests (the value types live in domain); events/outbound: outgoing command contract
├── access/domain/aggregates/apikey/                 ApiKey aggregate, domain events, PermissionChanges
├── access/application/events/                       ApiKeyProjectionMapper (domain events -> published commands)
├── access/application/usecase/{user,service}/<name>/   one test class per handler, in the handler's own package
├── access/application/usecase/                      test-only helpers: Fixtures, InMemoryApiKeys (a fake ApiKeys)
├── access/presentation/api/controllers/             REST controller and error handling (MockMvc, no database): ApiKeyControllerTest, ApiErrorHandlingTest; test-only VerifyProbeController
├── access/presentation/facade/                      ApiKeyFacadeImpl returns Success/Failure and never throws
├── access/infrastructure/persistence/               JPA mapping check (no database)
├── access/infrastructure/persistence/models/        outbox row mapping
├── access/infrastructure/persistence/repositories/  ApiKeysAdapter writes events to the outbox
├── access/infrastructure/keyformat/v1/              V1 credential issuer and verifier
├── access/infrastructure/config/                    key-format selection
└── integration/                                     real Postgres through the real application
```

### Unit tests (158)

#### Domain and common contracts

| Class (tests) | How | What it checks |
|---|---|---|
| `ApiKeyTest` (31) | Pure Java, no Spring, no mocks. Plain `ApiKey` objects and a fixed clock. | The aggregate's rules, grouped by method: **issue** (active key at revision 1, authenticating at once, readable by its owner); **ownership**; **canAuthenticate** (active and non-expiring keys authenticate; expired keys do not); **update** (each field alone, all at once, setting a scope to `NONE` revokes it, lifetime change restarts expiry, and every no-op case: same name, same level, same expiry, empty changes; no-ops bump nothing); **delete** (revision +1 and `ApiKeyDeleted`; nothing else about the key changes, because the caller removes the row); **domain events** (right `ApiKeyCreated`/`ApiKeyUpdated`/`ApiKeyDeleted` type, version, `occurredAt = now`, secret hash only on creation, nothing registered for no-ops, events handed over once); **state(now) and expiry**. |
| `PermissionChangesTest` (6) | Pure Java | Only the listed scopes change, `NONE` revokes, empty changes change nothing, `null` levels are dropped, an invalid level for a scope is rejected when the changes are built, the input map is copied. |
| `PermissionsTest` (10) | Pure Java | A never-granted scope is `NONE`, `with(...)` returns a copy, explicit `NONE` equals missing, `INGESTION` accepts only `WRITE` while `TENANT`/`CHANNEL` accept every level, `asMap()` is unmodifiable, the input map is copied. |
| `ScopeTest` (6) | Loops over `Scope.values()` | Every scope allows `NONE` and at least one real level, and every allowed level round-trips through `Permissions`. A new scope is covered automatically and fails here if it is declared inconsistently. |
| `ApiKeyProjectionMapperTest` (6) | Plain object test | Each domain event maps to the matching command (`ApiKeyCreated` -> create, `ApiKeyUpdated` -> update, `ApiKeyDeleted` -> delete) with the same fields, `emittedAt` left null, permissions as a scope-name to level-name map in scope-declaration order, no `NONE` entries, an unmodifiable map, and the wire names pinned. |
| `ApiKeyProjectionCommandContractTest` (7) | Reflection over the event records | The published event shape: exact field names and order for created, updated and deleted payloads, event-type strings (`apiKey.projection.create`, `apiKey.projection.update`, `apiKey.projection.delete`), the `expiresAt` field alongside both `occurredAt` and `emittedAt` and `createdAt`, `permissions` is a `Map<String, String>`, and `ApiKeyProjectionCommand` is sealed and permits exactly those three records. Changing a field fails this test on purpose: it is a contract change. |
| `ApiKeyFacadeImplTest` (3) | Plain object test with a mocked `VerifyApiKeyUseCase` | A `VerifiedApiKey` becomes `Success` with an `ApiKeyView` carrying the same identity and timestamps; domain `Permissions` become a string map (`{}` when empty); the handler's internal `ApiKeyVerificationFailure` becomes `Failure` with the same reason and message, and the facade does not throw. |

#### Use-case handlers (`access/application/usecase/{user,service}/<name>`)

Each handler test sits in its handler's package, because the handlers are package-private. They use `InMemoryApiKeys`
(a hand-written fake of the `ApiKeys` repository, not Mockito) and `Fixtures` (fixed clock, test user IDs, key
factories such as `active()`, a no-op `RetriedTransaction`), both public in
`access/application/usecase`. The fake behaves like the real
adapter in one important way: whenever a key is saved or deleted it collects the key's domain events, maps them through the real `ApiKeyProjectionMapper`, and appends the resulting commands to `publishedEvents`, so tests can assert exactly what would reach the outbox.

| Class (tests) | Main scenarios |
|---|---|
| `IssueApiKeyHandlerTest` (5) | Stores an active key and returns the secret once; registers exactly one `CreateApiKeyProjectionCommand` at version 1 carrying the hash; `NEVER` means no expiry, `ONE_DAY` expires tomorrow; the result's `toString` hides the secret. |
| `GetApiKeyHandlerTest` (5) | Owner sees an active key; a deleted key is not found; an unknown key and another user's key look identical (not found). |
| `UpdateApiKeyHandlerTest` (8) | A name change applies at once and emits one event; a permission patch touches only the given scopes; a lifetime change restarts expiry; **noopSameValue** and other no-ops save nothing and emit nothing; exhausted optimistic-lock retries become `ApiKeyUpdateFailure`. |
| `DeleteApiKeyHandlerTest` (4) | Removes the row at once and registers one `DeleteApiKeyProjectionCommand`; deleting the same key again is not found; unknown or foreign key is not found; exhausted retries become a conflict. |
| `VerifyApiKeyHandlerTest` (7) | An active key with the right secret authenticates; an update is visible to verification at once; unknown, expired and wrong-secret cases fail (an expired key is only revealed to whoever holds its secret); a deleted key looks unknown; a malformed access ID is a parsing failure; secrets never appear in `toString`. |

#### Adapters and config

| Class (tests) | How | What it checks |
|---|---|---|
| `ApiKeyControllerTest` (12) | Standalone MockMvc; the use case interfaces are small hand-written lambdas that record the commands they receive. No Spring context, no database. | Routing and JSON shapes: responses list every scope (`NONE` where never granted); a level a scope does not accept is 400 on create and patch; unknown scope or level names are 400; PATCH sends only the listed scopes and leaves omitted fields null in the command; GET never exposes a secret; delete reaches its use case. |
| `ApiErrorHandlingTest` (13) | Standalone MockMvc with the real exception handlers and the test-only `VerifyProbeController`. | RFC 9457 bodies and status mapping: validation field errors, malformed or wrong-type bodies, unknown routes and methods, unsupported media types, domain conflicts, and verification/parsing failures becoming 401 and 400 instead of 500. |
| `EventOutboxEntityTest` (4) | Plain object test | Each projection command (create, update, delete) maps to the right outbox row; the secret hash is only on created; permissions are stored as a JSON object keyed by scope. |
| `ApiKeysAdapterTest` (4) | Plain object test with mocked repositories | Saving or deleting a key writes its events to the outbox and drains them; no events writes nothing; a failing outbox write surfaces so the transaction rolls back. |
| `JpaMappingTest` (4) | Builds Hibernate metadata offline (no database) | Permissions are scope-to-level rows in the `access` schema; no per-scope columns are left; the outbox keeps permissions as one text column and carries `expires_at`; every entity table is in schema `access`. This checks the annotations only, not behaviour. |
| `V1CredentialTest` (5) | Pure Java | Issued credentials carry the `V1_` prefix and a hash of the secret; every credential is different; the verifier accepts the right secret and rejects others; a malformed stored hash never matches (returns false instead of throwing). |
| `ApiKeyConfigTest` (3) | Pure Java | New credentials use the configured key format; matching is case-insensitive; a format nobody can issue fails fast at startup. |
| `ApiKeyStatusTest` (1) | Pure Java | The response status derives from the aggregate state and the clock: active, expired. |

#### `ArchitectureTest` (14)

ArchUnit reads the compiled production classes and fails the build if a layer imports something it should not.
The rules enforced:

1. `domain` (including `domain.repositories`) depends only on itself, `java.*`, `jakarta.persistence`, Lombok, the UUID generator and JSpecify.
2. `domain` has no Spring dependency.
3. `common` never depends on the `access` domain, application, presentation or infrastructure layers; it may use `java.*`, Lombok, JSON and the Jakarta validation API.
4. `application` never depends on `presentation` or `infrastructure`.
5. The four layers only point inwards (ArchUnit layered architecture): `presentation` and `infrastructure` are used by no layer, `application` only by those two, `domain` only by `application`, `presentation` and `infrastructure`.
6. Use case interfaces (in `application.usecase..`) are implemented only inside `application.usecase..`.
7. `presentation` does not depend on `infrastructure`, `domain.repositories` or `application.interfaces`.
8. `infrastructure` does not depend on `presentation` or `application.usecase..` (the handlers).
9. `domain` never depends on `common`.
10. No field injection (`@Autowired`, `@Inject`).
11. `*Handler` classes in `application.usecase..` are not public.
12. No package cycles among the four layers inside `access`.
13. User use cases (`usecase.user..`) and service use cases (`usecase.service..`) never depend on each other.
14. The user API controller (`ApiKeyController`) only calls user use cases; the facade (`ApiKeyFacadeImpl`) only calls service use cases.

### Integration tests (33)

All of these run the real application against a throwaway Postgres 17. Each test starts with empty Access tables
(`TRUNCATE`) and a clock that can be pinned.

#### Helpers (not tests themselves)

| Class | Role |
|---|---|
| `PostgresIntegrationTest` | Base class: `@SpringBootTest`, tagged `integration`, starts the `postgres:17` Testcontainers container once per JVM and points the datasource at it. Pins the JVM time zone to `Asia/Kolkata` (same as `BackendApplication.main`) because the JDBC driver and Postgres disagree about the alias `Asia/Calcutta`. |
| `AccessIntegrationTest` | Base for the Access tests: wires every use case, a `JdbcTemplate`, MockMvc and the test clock; helpers `issueActive`, `view`, `count` and `outbox()` (reads the rows of the outbox table). |
| `TestClockConfig` + `MutableClock` | Replace the application `Clock` with one tests can pin to a moment or move forward, so expiry can be tested without waiting. |
| `access/presentation/api/controllers/VerifyProbeController` | Test-only endpoint `GET /test-probe/verify`. Nothing in the app exposes verification over HTTP, so this lets tests prove that verification and parsing failures become 401 and 400 instead of 500. It lives in the test sources only. |

#### Test classes

| Class (tests) | What it proves |
|---|---|
| `BackendApplicationTests` (1) | The whole Spring context starts against the container: bean wiring, entity mappings, the schema. |
| `AccessSchemaIntegrationTest` (4) | With the app's normal `ddl-auto=update` config: schema `access` exists and nothing was created in `public`; every table has exactly the expected columns (no `deleted_at`; the outbox carries a nullable `expires_at`); per-scope permission columns are gone; the pending-change and tombstone columns and the pending-change tables are gone; unique and foreign-key constraints exist. |
| `ApiKeyLifecycleIntegrationTest` (9) | The whole key lifecycle through the use case interfaces with a real database: issue (active at once, one `apiKey.projection.create` row, secret never stored), verify (success, and unknown/wrong-secret/expired/deleted all fail with the right codes), update (applied at once, `apiKey.projection.update` at version 2), no-op update (no event, no revision), delete (row and permission rows gone at once, `apiKey.projection.delete` at version 2, the Access ID is free again), a deleted key is not found for read/update/delete, ownership. |
| `ApiKeyAtomicityIntegrationTest` (3) | A database trigger refuses outbox rows for a key named `boom`, so the outbox write really fails. Then: a failed issue leaves no key or permission rows; a failed update leaves the key at its old revision; a failed delete leaves it active. This is the "key change and outbox row commit together or not at all" guarantee. |
| `ApiKeyConcurrencyIntegrationTest` (5, one test repeated 5 times) | 8 threads update the same key at once. Changes apply immediately and in sequence: every writer either lands its change or loses the optimistic-lock race; event versions stay unique and strictly increasing; the revision matches the number of landed changes; the last event describes the key's final state. |
| `ApiKeyHttpIntegrationTest` (7) | Full stack over MockMvc: create (active at once), read, patch (applied at once), delete, gone (all later operations are 404); the dismiss route no longer exists; 404 for unknown and foreign keys; 400 for a level a scope does not accept, unknown scope, unknown level and malformed body; 400 for a create without name or expiry (not a 500); 401 and 400 from verification failures through the test probe. |
| `SqlMigrationValidateIntegrationTest` (4) | Applies the hand-written `V1__create_access_schema.sql`, `V2__drop_pending_changes.sql` and `V3__remove_api_key_tombstones.sql` to a fresh database, then boots with `ddl-auto=validate` so Hibernate refuses to start on any mismatch. Also checks every time column is `timestamp with time zone`, that an `Instant` round-trips unchanged under a different session time zone, and that a full key lifecycle runs on the hand-written tables. |

### What the tests do NOT prove

- **The app has never been started with `BackendApplication.main` against the dev database.** The tests start the
  Spring context through `@SpringBootTest` on a container, which is close but not the same entry point.
- **`emitted_at` is only ever null.** No relay reads the outbox and publishes rows, so nothing fills it in. Tests
  check that it stays null, not that publishing works.
- **Nothing proves the data plane actually consumes the events.** Access only guarantees the outbox rows are
  correct, ordered and committed atomically; there is no consumer contract test yet.
- **The concurrency test does not say why a writer lost.** It accepts any `ApiKeyUpdateFailure`, which comes from
  the optimistic-lock retry running out. The outcome (unique monotonic versions, last event wins) is what is asserted.
- **Verification over real HTTP is only proven through a test-only probe.** The production app has no HTTP verify
  endpoint; `ApiKeyVerificationFacade` is in-process. The 401/400 mapping is tested via `VerifyProbeController`.
- **The owner `userId` comes from the URL path.** No authentication exists yet.
- **Key expiry is propagated but never enforced by the data plane.** Projection commands carry `expiresAt` and the
  outbox stores it, but there is no expiry event and no expiry scheduler; whether a projection refuses expired keys
  is the data plane's choice. Tests cover enforcement inside Access only (`canAuthenticate`).
- **`JpaMappingTest` is offline.** It validates annotations; the integration tests are what exercise the database.
- **No performance or load testing** beyond the 8-thread update race.

### Reading a failing test

| Symptom | Look at |
|---|---|
| `ApiKeyTest` or a handler test fails | The rule in `access/domain/aggregates/apikey/ApiKey.java`. Handlers only load, call and save. |
| `ApiKeyProjectionCommandContractTest` or `ApiKeyProjectionMapperTest` fails | Someone changed an event or command field, name, order or type string. That is a public contract change: confirm it is intended, then update the test, `ApiKeyProjectionMapper` and the Access section of this document. |
| `ArchitectureTest` names a class | A wrong import crossed a layer. Move the dependency behind an abstraction in `application` instead of loosening the rule. |
| `ScopeTest` or `PermissionsTest` fails after adding a scope | The new constant in `Scope` allows no valid level or omits `NONE`. |
| `ApiKeyControllerTest` fails | Routing, JSON shape or exception-to-status mapping in `presentation/api/controllers`. |
| `JpaMappingTest` fails | An entity annotation or table/column name changed. Also update the SQL file. |
| `SqlMigrationValidateIntegrationTest` fails | The mappings and the hand-written SQL files disagree. Fix whichever side is wrong. |
| `AccessSchemaIntegrationTest` fails | What `ddl-auto=update` generates differs from the expected tables and columns. |
| `ApiKeyAtomicityIntegrationTest` fails | The outbox write is no longer part of the key's transaction (`ApiKeysAdapter` no longer writes the outbox row in the caller's transaction). |
| An integration test fails to start | Is Docker running? The error will mention Testcontainers or the container image. They never need `localhost:5432`. |
| Time-dependent failures | The test clock (`MutableClock`) is pinned or not reset; `AccessIntegrationTest` resets it before each test. |
