# Access: class diagrams

These diagrams show which parts of the Access module call which, and the method each call goes through.

This is the reference for the Access module. Keep it in step with the code: when a component, a call between components, or a transaction boundary changes, update the matching diagram and table in the same commit.

- [Component interactions](#component-interactions) is the main reference: one diagram with every component, what each component contains, and the calls each operation makes, in order.
- [Transaction scopes](#transaction-scopes) shows where each transaction starts and ends, and what runs inside it.
- [Class-level detail](#class-level-detail) zooms in to individual classes, one flow at a time.
- [What is not reusable across contexts](#what-is-not-reusable-across-contexts) lists what to copy, not share, when another context needs an outbox.

All paths are under `backend/src/main/java/me/dhiren9939/backend/`.

How to read the arrows:

| Arrow | Meaning |
|---|---|
| `A --> B : m()` | A calls `m()` on B |
| `A ..\|> B` | A implements B |
| `A ..> B : creates / throws` | A creates, returns or throws B |

## Component interactions

Each box is a component. The text in `<<...>>` is its layer, and the members are the operations other components call. Colours: presentation is blue, application is green, domain is yellow, common (the published contract) is purple, infrastructure is grey, and external systems are white.

```mermaid
classDiagram
    direction TB

    class HttpClient["HTTP client"] {
        <<external>>
    }
    class OtherModules["Other modules"] {
        <<external>>
    }

    class Controller["ApiKeyController + DTOs"] {
        <<presentation>>
        +POST /api/v1/user/userId/apikey
        +GET /apikey/apiKeyId
        +PATCH /apikey/apiKeyId
        +DELETE /apikey/apiKeyId
    }
    class ErrorHandlers["Error handlers"] {
        <<presentation>>
        ApiKeyExceptionHandler
        WebExceptionHandler
        +ApiProblems.of(status, code, detail)$ ProblemDetail
    }
    class Facade["ApiKeyFacadeImpl"] {
        <<presentation>>
        +verifyApiKey(VerifyApiKeyQuery) ApiKeyVerifyResult
    }

    class VerificationContract["Verification contract"] {
        <<common>>
        +ApiKeyVerificationFacade.verifyApiKey(VerifyApiKeyQuery) ApiKeyVerifyResult
        ApiKeyVerifyResult = Success or Failure
        VerificationFailureCode
    }
    class IntegrationEvents["Integration events"] {
        <<common>>
        ApiKeyCreatedIntegrationEvent
        ApiKeyUpdatedIntegrationEvent
        ApiKeyDeletedIntegrationEvent
    }

    class OwnerUseCases["Owner use cases"] {
        <<application>>
        +IssueApiKeyUseCase.handle(IssueApiKeyCommand) IssuedApiKeyResult
        +GetApiKeyUseCase.handle(GetApiKeyQuery) ApiKeyView
        +UpdateApiKeyUseCase.handle(UpdateApiKeyCommand) ApiKeyView
        +DeleteApiKeyUseCase.handle(DeleteApiKeyCommand) void
    }
    class VerifyUseCase["Verify use case"] {
        <<application>>
        +VerifyApiKeyUseCase.handle(VerifyApiKeyQuery) VerifiedApiKey
        throws ApiKeyVerificationFailure
    }
    class RetriedTransaction {
        <<application>>
        +execute(Supplier~T~) T
        +execute(Runnable) void
    }
    class ApiKeyView {
        <<application>>
        +of(ApiKey, now)$ ApiKeyView
    }
    class EventMapper["Event mapper"] {
        <<application>>
        +toIntegrationEvent(ApiKeyEvent) ApiKeyIntegrationEvent
    }
    class Ports {
        <<application>>
        +IssueCredentialPort.issue() IssuedCredential
        +CredentialVerifier.supports(keyFormat) boolean
        +CredentialVerifier.matches(secret, storedHash) boolean
    }

    class Aggregate["ApiKey aggregate"] {
        <<domain>>
        +issue(userId, name, credential, permissions, createdAt, expiresAt)$ ApiKey
        +update(ApiKeyChanges, now) boolean
        +delete(now) void
        +pullDomainEvents() List~ApiKeyEvent~
        +getPermissions() Permissions
        +isOwnedBy(userId) boolean
        +state(now) ApiKeyState
        +canAuthenticate(now) boolean
    }
    class ValueTypes["Value types"] {
        <<domain>>
        +Permissions.of(map) / with(scope, level) / levelOf(scope) / asMap()
        +PermissionChanges.applyTo(Permissions) Permissions
        +Scope.requireAllowed(AccessLevel) void
        +Lifetime.expiryFrom(now) Instant
    }
    class DomainEvents["Domain events"] {
        <<domain>>
        ApiKeyCreated
        ApiKeyUpdated
        ApiKeyDeleted
    }
    class ApiKeys {
        <<domain>>
        +findById(apiKeyId) Optional~ApiKey~
        +findByAccessId(accessId) Optional~ApiKey~
        +save(ApiKey) ApiKey
        +delete(ApiKey) void
        +requireOwned(userId, apiKeyId) ApiKey
    }
    class DomainExceptions["Domain exceptions"] {
        <<domain>>
        ApiKeyNotFoundException
        ApiKeyUpdateFailure
        InvalidPermissionsException
    }

    class ApiKeysAdapter["ApiKeysAdapter + JpaApiKeyRepository"] {
        <<infrastructure>>
    }
    class Outbox["Outbox"] {
        <<infrastructure>>
        +EventOutboxEntity.of(eventId, event, payload)$ EventOutboxEntity
        +EventOutboxRepository.save(row)
        +EventOutboxRepository.lockPending(limit)
    }
    class Serializer["IntegrationEventSerializer"] {
        <<infrastructure>>
        +toJson(eventId, event) String
        +withEmittedAt(payload, at) String
    }
    class OutboxStore {
        <<infrastructure>>
        +append(event) void
        +lockPending(limit) List
        +markEmitted(row, at, body) void
        +recordFailure(row, error) void
    }
    class OutboxRelay["OutboxRelay + OutboxRelayHealth"] {
        <<infrastructure>>
        +tick() void every 2s
        +halt() Optional~Halt~
    }
    class ProjectionStream["ProjectionStream + RabbitProjectionStream"] {
        <<infrastructure>>
        +declare() void
        +send(row, body) CompletableFuture~Boolean~
    }
    class KeyFormats["Key formats"] {
        <<infrastructure>>
        +CredentialIssuer.keyFormat() / issue()
        V1CredentialIssuer
        V1CredentialVerifier
    }
    class Config {
        <<infrastructure>>
        +ApiKeyConfig.issueCredentialPort(issuers, format)
        +OutboxRelayConfig beans
    }

    class Postgres["Postgres access schema"] {
        <<external>>
    }
    class RabbitMQ["RabbitMQ apikey.projections"] {
        <<external>>
    }

    %% entry points
    HttpClient --> Controller : POST / GET / PATCH / DELETE
    OtherModules --> VerificationContract : verifyApiKey(query)
    Facade ..|> VerificationContract

    %% presentation to application
    Controller --> OwnerUseCases : handle(command or query)
    Controller --> ValueTypes : Permissions.of() / new PermissionChanges() / levelOf()
    Facade --> VerifyUseCase : handle(query)
    Facade --> ValueTypes : asMap()
    ErrorHandlers --> DomainExceptions : code() / getMessage()
    ErrorHandlers --> VerifyUseCase : ApiKeyVerificationFailure.code()

    %% owner use cases
    OwnerUseCases --> Ports : issue()
    OwnerUseCases --> ValueTypes : Lifetime.expiryFrom(createdAt)
    OwnerUseCases --> Aggregate : ApiKey.issue() / update(changes, now) / delete(now)
    OwnerUseCases --> ApiKeys : requireOwned() / save(key) / delete(key)
    OwnerUseCases --> RetriedTransaction : execute(work)
    OwnerUseCases --> ApiKeyView : of(key, now)
    RetriedTransaction ..> DomainExceptions : throws ApiKeyUpdateFailure
    ApiKeyView --> Aggregate : getters / state(now)

    %% verify use case
    VerifyUseCase --> Ports : supports(keyFormat) / matches(secret, hash)
    VerifyUseCase --> ApiKeys : findByAccessId(accessId)
    VerifyUseCase --> Aggregate : getAccessSecretHash() / canAuthenticate(now)

    %% domain
    Aggregate --> ValueTypes : Permissions.of() / applyTo() / expiryFrom()
    Aggregate ..> DomainEvents : registers on issue / update / delete
    ValueTypes ..> DomainExceptions : throws InvalidPermissionsException
    ApiKeys --> Aggregate : isOwnedBy(userId)
    ApiKeys ..> DomainExceptions : throws ApiKeyNotFoundException

    %% write path into the outbox
    ApiKeysAdapter ..|> ApiKeys
    ApiKeysAdapter --> Postgres : JPA save / delete / find
    ApiKeysAdapter --> Aggregate : pullDomainEvents()
    ApiKeysAdapter --> EventMapper : toIntegrationEvent(event)
    ApiKeysAdapter --> OutboxStore : append(integrationEvent)
    OutboxStore --> Serializer : toJson(eventId, event)
    OutboxStore --> Outbox : EventOutboxEntity.of() / save(row)
    EventMapper --> DomainEvents : getters
    EventMapper --> ValueTypes : asMap()
    EventMapper ..> IntegrationEvents : builder().build()
    Outbox --> Postgres : access.outbox

    %% key formats and wiring
    KeyFormats ..|> Ports
    Config --> KeyFormats : keyFormat()
    Config ..> Ports : creates IssueCredentialPort
    Config ..> OutboxRelay : creates relay / stream / health

    %% relay out of the outbox
    OutboxRelay --> OutboxStore : lockPending() / markEmitted() / recordFailure()
    OutboxRelay --> Serializer : withEmittedAt(payload, at)
    OutboxRelay --> ProjectionStream : declare() / send(row, body)
    OutboxStore --> Outbox : lockPending(limit) / setters
    ProjectionStream --> RabbitMQ : super stream, routed by apiKeyId

    %% transaction boundaries
    note for OwnerUseCases "TX starts here\nIssue: @Transactional\nGet: @Transactional(readOnly)\nUpdate, Delete: via RetriedTransaction"
    note for RetriedTransaction "TX starts here\nREQUIRES_NEW per attempt\nretried on optimistic-lock conflict"
    note for VerifyUseCase "TX starts here\n@Transactional(readOnly)"
    note for OutboxRelay "TX starts here\nTransactionTemplate per tick\nRabbitMQ is NOT in the TX"
    note for ApiKeysAdapter "no TX of its own\njoins the caller's TX"

    %% layer colours (per-node style works in Mermaid 10 and 11)
    style Controller fill:#dbeafe,stroke:#1d4ed8,color:#000
    style ErrorHandlers fill:#dbeafe,stroke:#1d4ed8,color:#000
    style Facade fill:#dbeafe,stroke:#1d4ed8,color:#000
    style OwnerUseCases fill:#dcfce7,stroke:#15803d,color:#000
    style VerifyUseCase fill:#dcfce7,stroke:#15803d,color:#000
    style RetriedTransaction fill:#dcfce7,stroke:#15803d,color:#000
    style ApiKeyView fill:#dcfce7,stroke:#15803d,color:#000
    style EventMapper fill:#dcfce7,stroke:#15803d,color:#000
    style Ports fill:#dcfce7,stroke:#15803d,color:#000
    style Aggregate fill:#fef9c3,stroke:#a16207,color:#000
    style ValueTypes fill:#fef9c3,stroke:#a16207,color:#000
    style DomainEvents fill:#fef9c3,stroke:#a16207,color:#000
    style ApiKeys fill:#fef9c3,stroke:#a16207,color:#000
    style DomainExceptions fill:#fef9c3,stroke:#a16207,color:#000
    style VerificationContract fill:#f3e8ff,stroke:#7e22ce,color:#000
    style IntegrationEvents fill:#f3e8ff,stroke:#7e22ce,color:#000
    style ApiKeysAdapter fill:#e5e7eb,stroke:#374151,color:#000
    style Outbox fill:#e5e7eb,stroke:#374151,color:#000
    style Serializer fill:#e5e7eb,stroke:#374151,color:#000
    style OutboxStore fill:#e5e7eb,stroke:#374151,color:#000
    style OutboxRelay fill:#e5e7eb,stroke:#374151,color:#000
    style ProjectionStream fill:#e5e7eb,stroke:#374151,color:#000
    style KeyFormats fill:#e5e7eb,stroke:#374151,color:#000
    style Config fill:#e5e7eb,stroke:#374151,color:#000
    style HttpClient fill:#ffffff,stroke:#6b7280,color:#000,stroke-dasharray:4 3
    style OtherModules fill:#ffffff,stroke:#6b7280,color:#000,stroke-dasharray:4 3
    style Postgres fill:#ffffff,stroke:#6b7280,color:#000,stroke-dasharray:4 3
    style RabbitMQ fill:#ffffff,stroke:#6b7280,color:#000,stroke-dasharray:4 3
```

How to read it:

- **Everything points towards the domain.** Presentation and infrastructure depend on application, application depends on domain, and the domain depends on nothing else. `ArchitectureTest` fails the build if a dependency points the other way.
- **Infrastructure plugs in from the side.** It implements the interfaces that the inner layers declare (`ApiKeys`, `IssueCredentialPort`, `CredentialVerifier`), so the use cases never know about JPA, hashing or RabbitMQ.
- **The write path ends in the outbox.** Saving a key goes through `ApiKeysAdapter`, which writes the outbox row in the same transaction. Publishing to RabbitMQ is a separate path: `OutboxRelay` reads the outbox later.
- **`common` is the only thing other modules see.** It knows nothing about Access internals.

### What is inside each component

| Component | Classes | Job |
|---|---|---|
| ApiKeyController + DTOs | `ApiKeyController`, `CreateRequestDto`, `UpdateRequestDto`, `CreateResponseDto`, `GetResponseDto`, `GetPageResponseDto` (unused), `ApiKeyStatus`, `RequestPatterns` | REST endpoints for key owners. |
| Error handlers | `ApiKeyExceptionHandler`, `WebExceptionHandler`, `ApiProblems` | Turn exceptions into problem+json bodies that carry a `code`. |
| ApiKeyFacadeImpl | `ApiKeyFacadeImpl` | In-process verification for other modules. |
| Owner use cases | For each operation: `*UseCase`, `*Command`/`*Query` and a package-private `*Handler`; plus `IssuedApiKeyResult` | One package per operation under `application/usecase/user/`. |
| Verify use case | `VerifyApiKeyUseCase`, `VerifyApiKeyHandler`, `ParsedApiKey`, `VerifiedApiKey`, `ApiKeyVerificationFailure` | Checks a credential. Lives under `application/usecase/service/`. |
| RetriedTransaction | `RetriedTransaction`, `OptimisticLockRetry` | Opens a new transaction for each attempt and retries when another writer changed the key first. |
| ApiKeyView | `application/model/ApiKeyView` | The secret-free read model that the owner use cases return. |
| Event mapper | `ApiKeyIntegrationEventMapper`, `DefaultApiKeyIntegrationEventMapper` | Maps a domain event to an integration event. This is where to encode or encrypt fields. |
| Ports | `IssueCredentialPort`, `CredentialVerifier` | What the use cases need from infrastructure, apart from storage. |
| ApiKey aggregate | `ApiKey` | Every business rule. A change applies at once, bumps the revision and registers an event. |
| Value types | `Permissions`, `PermissionChanges`, `ApiKeyChanges`, `Scope`, `AccessLevel`, `Lifetime`, `ApiKeyState`, `IssuedCredential` | Immutable pieces of the aggregate. |
| Domain events | `ApiKeyEvent` (sealed), `ApiKeyCreated`, `ApiKeyUpdated`, `ApiKeyDeleted` | A full snapshot of the key at each revision. Checked when created (`ApiKeyEventRules`): nothing null, text non-blank and at most 256 characters, `version` at least 1, `occurredAt` not before `createdAt`, `expiresAt` null or after `createdAt`. |
| ApiKeys | `ApiKeys` | The storage contract the domain defines. |
| Domain exceptions | `ApiKeyNotFoundException`, `ApiKeyUpdateFailure`, `UpdateFailureCode`, `InvalidPermissionsException` | The error handlers map these to 404 / 409 / 400. |
| Verification contract | `ApiKeyVerificationFacade`, `VerifyApiKeyQuery`, `ApiKeyVerifyResult`, `common.access.ApiKeyView`, `VerificationFailureCode` | What other modules call, and what they get back. |
| Integration events | `ApiKeyIntegrationEvent` (sealed), `ApiKeyCreated/Updated/DeletedIntegrationEvent` | The published event contract (`apiKey.projection.*`). Checked when created (`IntegrationEventRules`) with the same rules, plus `INGESTION` only allows `WRITE`; `IntegrationEventSerializer` then validates the whole JSON (exact keys, canonical UUIDs and instants, only `expiresAt` nullable) before it is stored and again before it is published. |
| ApiKeysAdapter | `ApiKeysAdapter`, `JpaApiKeyRepository` | Implements `ApiKeys`. After each save or delete it maps the key's events and hands each one to `OutboxStore.append`. |
| Outbox | `EventOutboxEntity`, `EventOutboxRepository` | The `access.outbox` table. |
| OutboxStore | `OutboxStore` | The outbox operations. `append` serializes the event and inserts the row in the caller's transaction. For the relay: lock pending rows, stamp a row as emitted with the body that was sent, record why a row could not be published. |
| IntegrationEventSerializer | `IntegrationEventSerializer`, `IntegrationEventSerializationException` | Writes the event as the JSON body and checks it against the whole contract, before it is stored (`toJson`) and again before it is published (`withEmittedAt`). |
| OutboxRelay | `OutboxRelay`, `OutboxRelayHealth`, `OutboxRelayProperties` | Every 2 seconds publishes up to 100 pending rows with publisher confirms. Cancels the batch on a broker outage; halts (health DOWN) on a refused or unpublishable row. Never retries. |
| ProjectionStream | `ProjectionStream`, `RabbitProjectionStream`, `ProjectionMessages`, `ProjectionRefusedException` | The super stream, routed by `apiKeyId`. Builds the message and tells a broker refusal from an outage. |
| Key formats | `CredentialIssuer`, `V1CredentialIssuer`, `V1CredentialVerifier`, `Sha256SecretHasher`, `SortableEncoder` | Generates and checks credentials. |
| Config | `ApiKeyConfig`, `OutboxRelayConfig`, `BackendApplication` | Wiring: the clock, the current key format and the relay. |

### Calls per operation, in order

The diagram shows who calls whom. This table shows the order of those calls for each operation. → means "calls".

| Operation | Call sequence |
|---|---|
| **Issue** (`POST`) | Controller → `IssueApiKeyUseCase.handle()` (one `@Transactional`) → `IssueCredentialPort.issue()` (Key formats: V1 issuer, SHA-256 hash) → `Lifetime.expiryFrom(now)` → `ApiKey.issue()` (registers `ApiKeyCreated`) → `ApiKeys.save()` → ApiKeysAdapter: JPA save, `pullDomainEvents()`, `toIntegrationEvent()`, `OutboxStore.append()` (`IntegrationEventSerializer.toJson()`, `EventOutboxEntity.of()`, `save()`) → `ApiKeyView.of()` → `IssuedApiKeyResult` (the only time the secret is returned) |
| **Get** (`GET`) | Controller → `GetApiKeyUseCase.handle()` (read-only transaction) → `ApiKeys.requireOwned()` → `ApiKey.isOwnedBy()` → `ApiKeyView.of()` → `ApiKey.state(now)` |
| **Update** (`PATCH`) | Controller → `UpdateApiKeyUseCase.handle()` → `RetriedTransaction.execute()` (new transaction per attempt, up to 2 retries) → `ApiKeys.requireOwned()` → `ApiKey.update(changes, now)` → `PermissionChanges.applyTo()` / `Lifetime.expiryFrom()`. If something changed: registers `ApiKeyUpdated` → `ApiKeys.save()` → same outbox write as Issue. Then `ApiKeyView.of()` |
| **Delete** (`DELETE`) | Controller → `DeleteApiKeyUseCase.handle()` → `RetriedTransaction.execute()` → `ApiKeys.requireOwned()` → `ApiKey.delete(now)` (registers `ApiKeyDeleted`) → `ApiKeys.delete()` → ApiKeysAdapter: JPA delete, then the same outbox write |
| **Verify** (other module) | `ApiKeyVerificationFacade.verifyApiKey()` → ApiKeyFacadeImpl → `VerifyApiKeyUseCase.handle()` → `ParsedApiKey.parse()` → `CredentialVerifier.supports(format)` → `ApiKeys.findByAccessId()` → `CredentialVerifier.matches(secret, hash)` → `ApiKey.canAuthenticate(now)` → `VerifiedApiKey` → facade: permissions as a scope-to-level map → `Success`. Any `ApiKeyVerificationFailure` → `Failure(code, message)` |
| **Relay** (scheduled) | `OutboxRelay.tick()` (skipped while halted; one transaction) → `ProjectionStream.declare()` → `OutboxStore.lockPending(100)` → per row: `IntegrationEventSerializer.withEmittedAt(payload, at)` → `ProjectionStream.send(row, body)` for all → wait for every confirm against one 5 s deadline → `markEmitted(row, at, body)` for confirmed rows. An outage rolls the tick back; a refused or unpublishable row gets `recordFailure()` and the relay halts |
| **Errors** | Exceptions propagate to `ApiKeyExceptionHandler`: `ApiKeyNotFoundException` → 404, `ApiKeyUpdateFailure` → 409, `InvalidPermissionsException` → 400, `ApiKeyVerificationFailure` → 400/401. Anything else goes to `WebExceptionHandler`, and all of them go through `ApiProblems.of()` |

## Transaction scopes

Only the four places marked "TX starts here" in the component diagram open a transaction. Nothing in presentation or infrastructure opens one, except the relay. `ApiKeysAdapter` and the Spring Data repositories join whichever transaction is already open, so the key row and its outbox rows are always committed together, or not at all.

| Operation | Who opens the transaction | Propagation | What runs inside it | Retried? |
|---|---|---|---|---|
| Issue | `IssueApiKeyHandler.handle` (`@Transactional`) | REQUIRED | Generate the credential, `ApiKey.issue()`, insert `api_keys` + `api_key_permissions`, insert the outbox row, build the view | No |
| Get | `GetApiKeyHandler.handle` (`@Transactional(readOnly = true)`) | REQUIRED, read-only | `requireOwned()`, `ApiKeyView.of()` | No |
| Update | `RetriedTransaction.execute` (`TransactionTemplate`) | REQUIRES_NEW, one per attempt | `requireOwned()`, `ApiKey.update()`, update the key + insert the outbox row if anything changed, build the view | Up to 2 retries (3 attempts) on `ObjectOptimisticLockingFailureException`, then `ApiKeyUpdateFailure` (409) |
| Delete | `RetriedTransaction.execute` (`TransactionTemplate`) | REQUIRES_NEW, one per attempt | `requireOwned()`, `ApiKey.delete()`, delete the key + insert the outbox row | Same as Update |
| Verify | `VerifyApiKeyHandler.handle` (`@Transactional(readOnly = true)`) | REQUIRED, read-only | Parse, `findByAccessId()`, `matches()`, `canAuthenticate()` | No. The facade maps the result after the transaction has closed |
| Relay | `OutboxRelay.tick` through `TransactionOperations.execute` | REQUIRED, one per tick | Lock pending rows (`FOR UPDATE SKIP LOCKED`), publish, wait for confirms, then stamp confirmed rows and record failures | Never. An outage rolls back and the next tick polls the same rows; a refusal halts the relay until restart |

### Issue: one transaction

The aggregate change and the outbox insert are flushed together at commit. The controller builds the HTTP response after the commit.

```mermaid
sequenceDiagram
    autonumber
    participant C as ApiKeyController
    participant H as IssueApiKeyHandler
    participant P as IssueCredentialPort
    participant K as ApiKey
    participant A as ApiKeysAdapter
    participant M as EventMapper
    participant DB as Postgres

    C->>H: handle(IssueApiKeyCommand)
    rect rgba(34, 197, 94, 0.15)
        Note over H,DB: TX: @Transactional (REQUIRED)
        H->>P: issue()
        P-->>H: IssuedCredential
        H->>K: ApiKey.issue(...)
        Note right of K: registers ApiKeyCreated
        H->>A: save(key)
        A->>DB: persist api_keys + api_key_permissions
        A->>K: pullDomainEvents()
        A->>M: toIntegrationEvent(event)
        A->>DB: insert access.outbox row
        H->>H: ApiKeyView.of(key, now)
        Note over H,DB: COMMIT: key and outbox row together
    end
    H-->>C: IssuedApiKeyResult
```

### Update and Delete: a new transaction for each attempt

Each attempt reads the key again, so the domain rules always decide against the current state. A conflict is only detected at commit, when the `UPDATE` or `DELETE ... WHERE lock_version = ?` matches no row. The commit then fails, `@Retryable` on `OptimisticLockRetry` runs the attempt again, and it gets a brand-new transaction. Delete follows the same steps but calls `key.delete(now)` and `apiKeys.delete(key)`.

```mermaid
sequenceDiagram
    autonumber
    participant C as ApiKeyController
    participant H as UpdateApiKeyHandler
    participant R as RetriedTransaction
    participant L as OptimisticLockRetry
    participant A as ApiKeysAdapter
    participant K as ApiKey
    participant DB as Postgres

    C->>H: handle(UpdateApiKeyCommand)
    H->>R: execute(updateOnce)
    R->>L: execute(supplier)
    loop up to 3 attempts (maxRetries = 2)
        rect rgba(59, 130, 246, 0.15)
            Note over L,DB: TX: TransactionTemplate REQUIRES_NEW
            L->>A: requireOwned(userId, apiKeyId)
            A->>DB: select api_keys
            L->>K: update(changes, now)
            alt something changed
                Note right of K: revision++, registers ApiKeyUpdated
                L->>A: save(key)
                A->>DB: insert access.outbox row
            else nothing changed
                Note right of K: no event, no write
            end
            L->>L: ApiKeyView.of(key, now)
            Note over L,DB: COMMIT: UPDATE ... WHERE lock_version = ?<br/>no row matched → ObjectOptimisticLockingFailureException → next attempt
        end
    end
    alt committed
        R-->>H: ApiKeyView
        H-->>C: ApiKeyView
    else retries exhausted
        R-->>C: throws ApiKeyUpdateFailure (409)
    end
```

### Relay: one transaction per tick, with RabbitMQ outside it

The relay holds row locks while it publishes and waits for confirms (one deadline for the whole batch, `confirmTimeout`, 5 seconds by default). `SKIP LOCKED` lets another instance's relay take other rows in the meantime. RabbitMQ is not part of the transaction, so delivery is at-least-once:

- If the publish succeeds but the commit fails, the rows stay pending and are sent again.
- On an outage (the stream cannot be declared, a send fails, the deadline passes, a confirm fails for a connection reason) the transaction rolls back: nothing is stamped or counted, and messages already confirmed in that tick are sent again by the next one.
- On a refusal or an unpublishable payload the transaction commits the stamps of the confirmed rows and the error on the failing row, then the relay halts. Nothing is retried.

Consumers keep the highest `version` per key and can dedupe on `eventId`.

```mermaid
sequenceDiagram
    autonumber
    participant S as Scheduler
    participant R as OutboxRelay
    participant O as OutboxStore
    participant J as IntegrationEventSerializer
    participant P as ProjectionStream
    participant DB as Postgres
    participant MQ as RabbitMQ

    S->>R: tick() every poll-interval (2s, fixed delay)
    Note right of R: returns at once while halted
    rect rgba(234, 179, 8, 0.18)
        Note over R,DB: TX: TransactionTemplate (REQUIRED), one per tick
        R->>P: declare()
        P--)MQ: create super stream if missing
        R->>O: lockPending(100)
        O->>DB: SELECT ... ORDER BY occurred_at, change_version FOR UPDATE SKIP LOCKED
        loop each row, in order
            R->>J: withEmittedAt(payload, at)
            Note right of J: a broken payload stops the loop:<br/>that row halts the relay
        end
        R->>P: send(row, body) for every built row
        P--)MQ: publish (not in the DB TX)
        MQ--)P: publish confirms
        Note over R,P: one 5s deadline for all confirms
        alt outage (no answer, deadline passed)
            Note over R,DB: ROLLBACK: nothing stamped or counted
        else all confirmed
            R->>O: markEmitted(row, at, body)
            Note over R,DB: COMMIT
        else refused or unpublishable
            R->>O: markEmitted(row, at, body) for confirmed rows
            R->>O: recordFailure(row, error) for the failing rows
            Note over R,DB: COMMIT, then HALT (health DOWN until restart)
        end
    end
```

## Class-level detail

The rest of the page zooms in to individual classes, one flow at a time:

1. [Owner API](#1-owner-api-issue-get-update-delete): issuing, reading, updating and deleting a key, up to the outbox insert.
2. [Verification](#2-verification): how other modules check a presented credential.
3. [Key formats](#3-key-formats): how credentials are generated and hashed.
4. [Outbox relay](#4-outbox-relay): how outbox rows reach RabbitMQ.
5. [Domain model](#5-domain-model): what goes on inside the `ApiKey` aggregate.
6. [Errors](#6-errors): which exception becomes which HTTP response.

`ApiKey` and `ApiKeys` appear without members in diagrams 1 to 4. Their members are listed in diagram 5.

All paths are under `backend/src/main/java/me/dhiren9939/backend/`.

## 1. Owner API (issue, get, update, delete)

The controller turns each request into a command or query and hands it to a use case. Handlers that write wrap their work in `RetriedTransaction`, so if an optimistic-lock conflict occurs, the key is read again and the domain rules run again against its current state. Each `ApiKeys.save` or `ApiKeys.delete` also writes the key's pending events to the outbox in the same transaction.

```mermaid
classDiagram
    direction TB

    class ApiKeyController {
        +create(userId, CreateRequestDto) ResponseEntity
        +get(userId, apiKeyId) GetResponseDto
        +update(userId, apiKeyId, UpdateRequestDto) GetResponseDto
        +delete(userId, apiKeyId) ResponseEntity
    }

    class IssueApiKeyUseCase {
        <<interface>>
        +handle(IssueApiKeyCommand) IssuedApiKeyResult
    }
    class GetApiKeyUseCase {
        <<interface>>
        +handle(GetApiKeyQuery) ApiKeyView
    }
    class UpdateApiKeyUseCase {
        <<interface>>
        +handle(UpdateApiKeyCommand) ApiKeyView
    }
    class DeleteApiKeyUseCase {
        <<interface>>
        +handle(DeleteApiKeyCommand) void
    }

    class IssueApiKeyHandler
    class GetApiKeyHandler
    class UpdateApiKeyHandler
    class DeleteApiKeyHandler

    class RetriedTransaction {
        +execute(Supplier~T~) T
        +execute(Runnable) void
    }
    class OptimisticLockRetry {
        +execute(Supplier~T~) T
    }
    class IssueCredentialPort {
        <<interface>>
        +issue() IssuedCredential
    }
    class Lifetime {
        <<enumeration>>
        +expiryFrom(now) Instant
    }
    class ApiKeyView {
        <<record>>
        +of(ApiKey, now)$ ApiKeyView
    }
    class IssuedApiKeyResult {
        <<record>>
        key ApiKeyView
        accessSecret String
    }
    class ApiKeys {
        <<interface>>
    }
    class ApiKey {
        <<aggregate>>
    }

    ApiKeyController --> IssueApiKeyUseCase : handle(IssueApiKeyCommand)
    ApiKeyController --> GetApiKeyUseCase : handle(GetApiKeyQuery)
    ApiKeyController --> UpdateApiKeyUseCase : handle(UpdateApiKeyCommand)
    ApiKeyController --> DeleteApiKeyUseCase : handle(DeleteApiKeyCommand)

    IssueApiKeyHandler ..|> IssueApiKeyUseCase
    GetApiKeyHandler ..|> GetApiKeyUseCase
    UpdateApiKeyHandler ..|> UpdateApiKeyUseCase
    DeleteApiKeyHandler ..|> DeleteApiKeyUseCase

    IssueApiKeyHandler --> IssueCredentialPort : issue()
    IssueApiKeyHandler --> Lifetime : expiryFrom(createdAt)
    IssueApiKeyHandler --> ApiKey : ApiKey.issue(...)
    IssueApiKeyHandler --> ApiKeys : save(key)
    IssueApiKeyHandler ..> IssuedApiKeyResult : creates

    GetApiKeyHandler --> ApiKeys : requireOwned(userId, apiKeyId)

    UpdateApiKeyHandler --> RetriedTransaction : execute(work)
    UpdateApiKeyHandler --> ApiKeys : requireOwned() / save(key)
    UpdateApiKeyHandler --> ApiKey : update(changes, now)

    DeleteApiKeyHandler --> RetriedTransaction : execute(work)
    DeleteApiKeyHandler --> ApiKeys : requireOwned() / delete(key)
    DeleteApiKeyHandler --> ApiKey : delete(now)

    IssueApiKeyHandler --> ApiKeyView : of(key, now)
    GetApiKeyHandler --> ApiKeyView : of(key, now)
    UpdateApiKeyHandler --> ApiKeyView : of(key, now)
    ApiKeyView --> ApiKey : getters / state(now)

    RetriedTransaction --> OptimisticLockRetry : execute(supplier)
```

### Saving a key writes its events to the outbox

Diagram 1 stops at the `ApiKeys` port. `ApiKeysAdapter` implements that port. When it saves or deletes a key, it also drains the key's domain events, maps each one to the published integration event and hands it to `OutboxStore.append`, which serializes it (checking it against the contract) and inserts the row. All of this happens in the caller's transaction, so a serialization failure rolls the key change back.

```mermaid
classDiagram
    direction LR

    class ApiKeys {
        <<interface>>
        +findById(apiKeyId) Optional~ApiKey~
        +findByAccessId(accessId) Optional~ApiKey~
        +save(ApiKey) ApiKey
        +delete(ApiKey) void
        +require(apiKeyId) ApiKey
        +requireOwned(userId, apiKeyId) ApiKey
    }
    class ApiKeysAdapter
    class JpaApiKeyRepository {
        <<interface>>
        +findByAccessId(accessId) Optional~ApiKey~
    }
    class ApiKey {
        <<aggregate>>
    }
    class ApiKeyIntegrationEventMapper {
        <<interface>>
        +toIntegrationEvent(ApiKeyEvent) ApiKeyIntegrationEvent
    }
    class DefaultApiKeyIntegrationEventMapper
    class ApiKeyIntegrationEvent {
        <<common contract>>
        Created / Updated / Deleted
    }
    class OutboxStore {
        +append(ApiKeyIntegrationEvent) void
    }
    class IntegrationEventSerializer {
        +toJson(eventId, event) String
    }
    class EventOutboxEntity {
        <<entity>>
        +of(eventId, event, payload)$ EventOutboxEntity
    }
    class EventOutboxRepository {
        <<interface>>
    }

    ApiKeysAdapter ..|> ApiKeys
    ApiKeysAdapter --> JpaApiKeyRepository : findById() / findByAccessId() / save() / delete()
    ApiKeysAdapter --> ApiKey : pullDomainEvents()
    ApiKeysAdapter --> ApiKeyIntegrationEventMapper : toIntegrationEvent(event)
    ApiKeysAdapter --> OutboxStore : append(integrationEvent)
    OutboxStore --> IntegrationEventSerializer : toJson(eventId, event)
    OutboxStore --> EventOutboxEntity : of(eventId, event, payload)
    OutboxStore --> EventOutboxRepository : save(row)

    DefaultApiKeyIntegrationEventMapper ..|> ApiKeyIntegrationEventMapper
    DefaultApiKeyIntegrationEventMapper ..> ApiKeyIntegrationEvent : builds
```

## 2. Verification

Other modules depend only on `ApiKeyVerificationFacade`, which lives in `common/access`. The facade never throws on a rejected credential: it converts `ApiKeyVerificationFailure` into a `Failure` result. The arrows from the handler are numbered in the order the handler runs them. The secret is checked before expiry, so a caller who doesn't hold the right secret can't tell an unknown access ID from a wrong secret.

```mermaid
classDiagram
    direction TB

    class ApiKeyVerificationFacade {
        <<interface>>
        +verifyApiKey(VerifyApiKeyQuery) ApiKeyVerifyResult
    }
    class ApiKeyFacadeImpl
    class ApiKeyVerifyResult {
        <<sealed common contract>>
        Success(ApiKeyView)
        Failure(VerificationFailureCode, message)
    }
    class CommonApiKeyView["ApiKeyView (common)"] {
        <<record>>
        permissions Map of scope to level
    }
    class VerifyApiKeyUseCase {
        <<interface>>
        +handle(VerifyApiKeyQuery) VerifiedApiKey
    }
    class VerifyApiKeyHandler
    class ParsedApiKey {
        <<record>>
        +parse(accessId, accessSecret)$ ParsedApiKey
    }
    class CredentialVerifier {
        <<interface>>
        +supports(keyFormat) boolean
        +matches(presentedSecret, storedSecretHash) boolean
    }
    class ApiKeys {
        <<interface>>
    }
    class ApiKey {
        <<aggregate>>
    }
    class VerifiedApiKey {
        <<record>>
    }
    class ApiKeyVerificationFailure {
        <<exception>>
        +malformed()$
        +invalid()$
        +expired()$
        +code() VerificationFailureCode
    }

    ApiKeyFacadeImpl ..|> ApiKeyVerificationFacade
    ApiKeyFacadeImpl --> VerifyApiKeyUseCase : handle(query)
    ApiKeyFacadeImpl ..> ApiKeyVerifyResult : Success / Failure
    ApiKeyFacadeImpl ..> CommonApiKeyView : creates
    ApiKeyFacadeImpl --> ApiKeyVerificationFailure : code() / getMessage()

    VerifyApiKeyHandler ..|> VerifyApiKeyUseCase
    VerifyApiKeyHandler --> ParsedApiKey : 1 parse(accessId, secret)
    VerifyApiKeyHandler --> CredentialVerifier : 2 supports(keyFormat)  4 matches(secret, hash)
    VerifyApiKeyHandler --> ApiKeys : 3 findByAccessId(accessId)
    VerifyApiKeyHandler --> ApiKey : getAccessSecretHash()  5 canAuthenticate(now)
    VerifyApiKeyHandler ..> VerifiedApiKey : creates
    VerifyApiKeyHandler ..> ApiKeyVerificationFailure : throws invalid() / expired()
    ParsedApiKey ..> ApiKeyVerificationFailure : throws malformed()
```

## 3. Key formats

`IssueCredentialPort` has no implementing class. `ApiKeyConfig.issueCredentialPort(...)` picks the `CredentialIssuer` whose format matches `access.key-format.current` (V1 by default) and returns that issuer's `issue` method as the port. To verify a credential, the use case asks every `CredentialVerifier` bean whether it `supports` the presented format.

```mermaid
classDiagram
    direction LR

    class ApiKeyConfig {
        <<configuration>>
        +issueCredentialPort(issuers, currentKeyFormat) IssueCredentialPort
        +secureRandom() SecureRandom
        +clock() Clock
    }
    class IssueCredentialPort {
        <<interface>>
        +issue() IssuedCredential
    }
    class CredentialIssuer {
        <<interface>>
        +keyFormat() String
        +issue() IssuedCredential
    }
    class CredentialVerifier {
        <<interface>>
        +supports(keyFormat) boolean
        +matches(presentedSecret, storedSecretHash) boolean
    }
    class V1CredentialIssuer
    class V1CredentialVerifier
    class SortableEncoder {
        +encode(long[]) String
    }
    class Sha256SecretHasher {
        +hash(accessSecret) byte[]
        +hashHex(accessSecret) String
    }
    class SecureRandom
    class IssuedCredential {
        <<record>>
        accessId
        accessSecret
        secretHash
    }

    ApiKeyConfig --> CredentialIssuer : keyFormat()
    ApiKeyConfig ..> IssueCredentialPort : creates as issuer.issue
    IssueCredentialPort --> CredentialIssuer : issue()

    V1CredentialIssuer ..|> CredentialIssuer
    V1CredentialIssuer --> SortableEncoder : encode(uuidBits) / encode(secretLongs)
    V1CredentialIssuer --> SecureRandom : nextLong()
    V1CredentialIssuer --> Sha256SecretHasher : hashHex(accessSecret)
    V1CredentialIssuer ..> IssuedCredential : creates

    V1CredentialVerifier ..|> CredentialVerifier
    V1CredentialVerifier --> Sha256SecretHasher : hash(presentedSecret)
```

## 4. Outbox relay

Diagram 1 shows how rows get into the outbox. This diagram shows how they get out. Every `poll-interval` (2 seconds, fixed delay), `OutboxRelay.tick()` runs one batch in its own transaction. It declares the `apikey.projections` super stream, locks up to 100 pending rows with `FOR UPDATE SKIP LOCKED`, builds each body with `IntegrationEventSerializer.withEmittedAt` (one `emittedAt` per tick, truncated to microseconds so the column and the JSON match), sends them all (partitioned by `apiKeyId`) and waits for the publish confirms against one deadline for the whole batch. Then it stamps confirmed rows with the exact body that was sent. An outage rolls the tick back; a refused or unpublishable row is recorded on the row and halts the relay until restart. `OutboxRelayConfig` wires these beans.

```mermaid
classDiagram
    direction TB

    class OutboxRelay {
        +tick() void
        +halt() Optional~Halt~
        -relayBatch(status) Halt
    }
    class Halt {
        <<record>>
        eventId
        eventType
        error
    }
    class OutboxRelayHealth {
        +health() Health
    }
    class TransactionOperations {
        <<spring>>
        +execute(callback) T
    }
    class OutboxStore {
        +lockPending(limit) List~EventOutboxEntity~
        +markEmitted(row, at, body) void
        +recordFailure(row, error) void
    }
    class IntegrationEventSerializer {
        +withEmittedAt(payload, at) String
    }
    class ProjectionStream {
        <<interface>>
        +declare() void
        +send(row, body) CompletableFuture~Boolean~
    }
    class RabbitProjectionStream
    class ProjectionMessages {
        +toMessage(row, body, MessageBuilder)$ Message
    }
    class ProjectionRefusedException {
        <<exception>>
    }
    class RabbitStreamTemplate {
        <<spring amqp>>
    }
    class Environment {
        <<rabbit stream>>
    }
    class EventOutboxRepository {
        <<interface>>
        +lockPending(limit) List~EventOutboxEntity~
    }
    class EventOutboxEntity {
        <<entity>>
        +emitted(at, body) void
    }
    class OutboxRelayProperties {
        <<record>>
        streamName
        partitions
        batchSize
        pollInterval
        confirmTimeout
    }

    OutboxRelay --> TransactionOperations : execute(relayBatch)
    OutboxRelay --> OutboxStore : lockPending() / markEmitted() / recordFailure()
    OutboxRelay --> IntegrationEventSerializer : withEmittedAt(payload, at)
    OutboxRelay --> ProjectionStream : declare() / send(row, body)
    OutboxRelay --> OutboxRelayProperties : batchSize() / confirmTimeout()
    OutboxRelay ..> Halt : creates on refusal
    OutboxRelayHealth --> OutboxRelay : halt()

    OutboxStore --> EventOutboxRepository : lockPending(limit)
    OutboxStore --> EventOutboxEntity : emitted(at, body) / setAttempts() / setLastError()

    RabbitProjectionStream ..|> ProjectionStream
    RabbitProjectionStream --> Environment : streamCreator().superStream().create()
    RabbitProjectionStream --> ProjectionMessages : toMessage(row, body, builder)
    RabbitProjectionStream --> RabbitStreamTemplate : messageBuilder() / send(message)
    RabbitProjectionStream ..> ProjectionRefusedException : broker answered with an error code
```

How a send ends, as `RabbitProjectionStream` reports it to the relay:

| Template answer | Port answer | Relay does |
|---|---|---|
| confirmed | `true` | stamps the row |
| `StreamSendException` with a client or connection code (producer closed or unavailable, confirm timeout, enqueue failed, stream not available or missing) | exceptional | outage: rolls the tick back, repolls next tick |
| `StreamSendException` with any other code | exceptional, `ProjectionRefusedException` | refusal: records the error on the row and halts |
| any other exception, or no answer before the deadline | exceptional / timeout | outage |

## 5. Domain model

Callers state what they want through the aggregate's commands; it has no setters. Each command bumps `revision` and registers a full-snapshot event, and `ApiKeysAdapter` collects those events with `pullDomainEvents()`. `Permissions` is immutable and holds one level per scope. Each `Scope` decides which levels it accepts.

```mermaid
classDiagram
    direction TB

    class ApiKey {
        <<aggregate>>
        -apiKeyId UUID
        -userId UUID
        -accessId String
        -accessSecretHash String
        -apiKeyName String
        -createdAt Instant
        -expiresAt Instant
        -revision long
        -lockVersion long
        +issue(userId, name, IssuedCredential, Permissions, createdAt, expiresAt)$ ApiKey
        +update(ApiKeyChanges, now) boolean
        +delete(now) void
        +pullDomainEvents() List~ApiKeyEvent~
        +getPermissions() Permissions
        +isOwnedBy(userId) boolean
        +state(now) ApiKeyState
        +canAuthenticate(now) boolean
    }
    class ApiKeys {
        <<interface>>
        +requireOwned(userId, apiKeyId) ApiKey
    }
    class IssuedCredential {
        <<record>>
    }
    class ApiKeyChanges {
        <<record>>
        name String
        lifetime Lifetime
        permissions PermissionChanges
    }
    class Lifetime {
        <<enumeration>>
        ONE_DAY
        ONE_MONTH
        NEVER
        +expiryFrom(now) Instant
    }
    class PermissionChanges {
        <<record>>
        +applyTo(Permissions) Permissions
        +isEmpty() boolean
    }
    class Permissions {
        +none()$ Permissions
        +of(Map)$ Permissions
        +levelOf(Scope) AccessLevel
        +with(Scope, AccessLevel) Permissions
        +asMap() Map
    }
    class Scope {
        <<enumeration>>
        INGESTION
        TENANT
        CHANNEL
        +allows(AccessLevel) boolean
        +requireAllowed(AccessLevel) void
    }
    class AccessLevel {
        <<enumeration>>
        NONE
        READ
        WRITE
        READ_WRITE
    }
    class ApiKeyState {
        <<enumeration>>
        ACTIVE
        EXPIRED
    }
    class ApiKeyEvent {
        <<sealed interface>>
        +version() long
        +eventType() String
    }
    class ApiKeyCreated
    class ApiKeyUpdated
    class ApiKeyDeleted
    class InvalidPermissionsException {
        <<exception>>
    }
    class ApiKeyNotFoundException {
        <<exception>>
    }

    ApiKey --> IssuedCredential : accessId() / secretHash()
    ApiKey --> ApiKeyChanges : name() / lifetime() / permissions()
    ApiKey --> Lifetime : expiryFrom(now)
    ApiKey --> PermissionChanges : applyTo(current)
    ApiKey --> Permissions : of(levels) / asMap()
    ApiKey ..> ApiKeyState : state(now)
    ApiKey ..> ApiKeyEvent : registers on issue / update / delete

    PermissionChanges --> Permissions : with(scope, level)
    PermissionChanges --> Scope : requireAllowed(level)
    Permissions --> Scope : requireAllowed(level)
    Scope --> AccessLevel : allowedLevels
    Scope ..> InvalidPermissionsException : throws

    ApiKeyCreated ..|> ApiKeyEvent
    ApiKeyUpdated ..|> ApiKeyEvent
    ApiKeyDeleted ..|> ApiKeyEvent

    ApiKeys --> ApiKey : isOwnedBy(userId)
    ApiKeys ..> ApiKeyNotFoundException : throws
```

## 6. Errors

Domain and application exceptions are allowed to propagate all the way up. `ApiKeyExceptionHandler` maps them to RFC 9457 problem responses that include a machine-readable `code`. `WebExceptionHandler` catches framework errors (validation, unreadable body, type mismatch) and anything else that is left.

```mermaid
classDiagram
    direction LR

    class ApiKeyExceptionHandler {
        <<controller advice>>
        ~notFound(ApiKeyNotFoundException) 404
        ~conflict(ApiKeyUpdateFailure) 409
        ~verificationFailed(ApiKeyVerificationFailure) 400 or 401
        ~invalidPermissions(InvalidPermissionsException) 400
    }
    class WebExceptionHandler {
        <<controller advice>>
        #handleMethodArgumentNotValid() 400
        #handleHttpMessageNotReadable() 400
        #handleTypeMismatch() 400
        ~unexpected(Exception) 500
    }
    class ApiProblems {
        +of(status, code, detail)$ ProblemDetail
    }
    class RetriedTransaction
    class ApiKeyUpdateFailure {
        <<exception>>
        +code() UpdateFailureCode
    }
    class ApiKeyNotFoundException {
        <<exception>>
    }
    class InvalidPermissionsException {
        <<exception>>
    }
    class ApiKeyVerificationFailure {
        <<exception>>
        +code() VerificationFailureCode
    }

    RetriedTransaction ..> ApiKeyUpdateFailure : throws when retries run out
    ApiKeyExceptionHandler --> ApiKeyUpdateFailure : code()
    ApiKeyExceptionHandler --> ApiKeyVerificationFailure : code()
    ApiKeyExceptionHandler ..> ApiKeyNotFoundException : handles
    ApiKeyExceptionHandler ..> InvalidPermissionsException : handles
    ApiKeyExceptionHandler --> ApiProblems : of(status, code, detail)
    WebExceptionHandler --> ApiProblems : of(status, code, detail)
```

## What is not reusable across contexts

The outbox relay, `OutboxStore` and the outbox tables belong to Access alone: they read `access.outbox` and publish to `apikey.projections`. When Subscriptions needs to publish events, copy the pattern (an outbox row written in the same transaction, a strict serializer in front of it, a relay with publisher confirms that halts on a row it cannot publish), not the classes.
