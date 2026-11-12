# Access: what the tests do

A guide to the tests under `backend/src/test`, for someone who has not read them. For the code itself see
[access-class-structure.md](access-class-structure.md); for the rules being tested see
[access-context.md](access-context.md).

## Running them

Run from `backend/`. Numbers below were measured on this repo (Windows, Docker Desktop running).

| What | Command | Tests | Time |
|---|---|---|---|
| Unit tests only | `./mvnw test -Dgroups='!integration'` | 209 | about 24 s of Maven time |
| Integration tests only | `./mvnw test -Dgroups=integration` | 41 | about 40 to 60 s (container start included; varies by machine) |
| Everything | `./mvnw test` | 250 | about 49 s |

- **Docker must be running for the integration tests.** They start a throwaway `postgres:17` container with
  Testcontainers (one container per test JVM, shared by all integration classes). They **never** use the
  `maindb` dev database from `docker-compose.yml`; a dev database on `localhost:5432` is not needed or touched.
- "Integration" means a class tagged `@Tag("integration")`. The tag sits on `PostgresIntegrationTest`
  (inherited by every subclass, including `BackendApplicationTests`) and on `SqlMigrationValidateIntegrationTest`.
  There is no special Maven profile; `-Dgroups` is the standard Surefire switch.
- Unit tests need no database, no Docker and no Spring application context. `ApiKeyConfigTest` builds
  `ApiKeyConfig` by hand, and `ApiKeyControllerTest` uses a standalone MockMvc setup.

## Where each kind of test lives

```
src/test/java/me/dhiren9939/backend
├── ArchitectureTest                                 layering rules (ArchUnit)
├── BackendApplicationTests                          Spring context loads on a real Postgres
├── shared/access/                                   Permissions, Scope; events/outbound: outgoing command contract
├── access/domain/aggregates/apikey/                 ApiKey aggregate, PermissionChanges
├── access/application/usecase/{user,service}/<name>/   one test class per handler, in the handler's own package
├── access/application/usecase/                      test-only helpers: Fixtures, InMemoryApiKeys (a fake ApiKeys)
├── access/presentation/api/controllers/             REST controller and error handling (MockMvc, no database): ApiKeyControllerTest, ApiErrorHandlingTest; test-only VerifyProbeController
├── access/infrastructure/persistence/               JPA mapping check (no database)
├── access/infrastructure/persistence/models/        outbox row mapping
├── access/infrastructure/persistence/repositories/  ApiKeysAdapter writes events to the outbox
├── access/infrastructure/keyformat/v1/              V1 credential issuer and verifier
├── access/infrastructure/config/                    key-format selection
└── integration/                                     real Postgres through the real application
```

## Unit tests (209)

### Domain and shared contracts

| Class (tests) | How | What it checks |
|---|---|---|
| `ApiKeyTest` (76) | Pure Java, no Spring, no mocks. Plain `ApiKey` objects and a fixed clock. | The aggregate's rules, grouped by method: **issue** (provisioning key at revision 1, readable by its owner but not yet authenticating); **canAuthenticate** (a key with a pending update still works with its old attributes; expired, provisioning and deleted keys do not); **requestUpdate** (each field alone, all at once, setting a scope to `NONE` revokes it, and every no-op case: same name, same level, same expiry, empty changes); **refusals** (update or delete refused while creating, pending, failed or deleted); **confirm** (creation activates, update applies, delete leaves a `DELETED` tombstone, a stale revision is refused); **reject** (records the reason, registers `ApiKeyChangeRejected` and then the compensating command at the next revision: delete for a failed creation, update of the confirmed state for a failed update, create for a failed delete; cancelling a pending change does the same with `CANCELLED_BY_USER`); **dismiss** (a failed update or delete clears, a failed creation or a tombstone is removed; none of them registers anything); **domain events** (right type, revision, `occurredAt = now`, `emittedAt = null`, secret hash only on `CreateApiKeyProjectionCommand`, `ApiKeyChangeConfirmed` on confirm, nothing registered for no-ops or refused changes, events handed over once); **desiredState** and **state()**. |
| `PermissionChangesTest` (6) | Pure Java | Only the listed scopes change, `NONE` revokes, empty changes change nothing, `null` levels are dropped, an invalid level for a scope is rejected when the changes are built, the input map is copied. |
| `PermissionsTest` (10) | Pure Java | A never-granted scope is `NONE`, `with(...)` returns a copy, explicit `NONE` equals missing, `INGESTION` accepts only `WRITE` while `TENANT`/`CHANNEL` accept every level, `asMap()` is unmodifiable, the input map is copied. |
| `ScopeTest` (6) | Loops over `Scope.values()` | Every scope allows `NONE` and at least one real level, and every allowed level round-trips through `Permissions`. A new scope is covered automatically and fails here if it is declared inconsistently. |
| `ApiKeyProjectionCommandContractTest` (7) | Reflection over the event records | The published event shape: exact field names and order for created, updated and deleted payloads, event-type strings (`apiKey.projection.create`, `apiKey.projection.update`, `apiKey.projection.delete`), no expiry field, both `occurredAt` and `emittedAt` present, permission names are wire values, and `ApiKeyProjectionCommand` is sealed and permits exactly those three records. Changing a field fails this test on purpose: it is a contract change. |

### Use-case handlers (`access/application/usecase/{user,service}/<name>`)

Each handler test sits in its handler's package, because the handlers are package-private. They use `InMemoryApiKeys`
(a hand-written fake of the `ApiKeys` repository, not Mockito) and `Fixtures` (fixed clock, test user IDs, key
factories such as `provisioning()` and `active()`, a no-op `RetriedTransaction`), both public in
`access/application/usecase`. The fake behaves like the real
adapter in one important way: whenever a key is saved or deleted it collects the events the key registered (projection commands, in
`publishedEvents`), so tests can assert exactly what would reach the outbox.

| Class (tests) | Main scenarios |
|---|---|
| `IssueApiKeyHandlerTest` (5) | Stores a provisioning key and returns the secret once; registers exactly one `CreateApiKeyProjectionCommand` at revision 1 carrying the hash; `NEVER` means no expiry, `ONE_DAY` expires tomorrow; the result's `toString` hides the secret. |
| `GetApiKeyHandlerTest` (6) | Owner sees an active key, a provisioning key, failed states and a `DELETED` tombstone; an unknown key and another user's key look identical (not found). |
| `UpdateApiKeyHandlerTest` (11) | A name change creates a pending update and one event; a permission patch touches only the given scopes; a lifetime change restarts expiry; **noopSameValue** and other no-ops save nothing and emit nothing; provisioning, pending, failed and deleted keys cannot be updated; exhausted optimistic-lock retries become `ApiKeyUpdateFailure`. |
| `DeleteApiKeyHandlerTest` (4) | Requests deletion and registers one `DeleteApiKeyProjectionCommand`; refused for provisioning, pending, failed and deleted keys; unknown or foreign key is not found. |
| `DismissApiKeyHandlerTest` (5) | Failed change: cleared, key stays, no event. Failed creation: key removed, no event (the delete went out when it was rejected). Tombstone: removed, no event. A healthy key has nothing to dismiss (refused). |
| `CancelApiKeyChangeHandlerTest` (6) | Cancelling a pending update or creation fails it as `CANCELLED_BY_USER` and emits the compensating command; nothing pending or already failed is refused; only the owner can cancel; exhausted retries become a conflict. |
| `ConfirmChangeHandlerTest` (6) | Confirming a creation activates the key and emits no projection command; confirming a delete leaves a visible tombstone; a stale revision, nothing pending, or an unknown key is refused; exhausted retries become a conflict. |
| `RejectChangeHandlerTest` (6) | Rejecting a creation fails provisioning and emits the compensating delete at revision 2; every `FailureReason` is recorded on reject; a stale revision, an unknown key or a missing reason is refused; exhausted retries become a conflict. |
| `VerifyApiKeyHandlerTest` (7) | An active key with the right secret authenticates; a pending update still authenticates with confirmed permissions; a pending delete still authenticates; unknown, provisioning, failed, deleted, expired and wrong-secret cases all fail with the **same** message; a malformed access ID is a parsing failure; secrets never appear in `toString`. |

### Adapters and config

| Class (tests) | How | What it checks |
|---|---|---|
| `ApiKeyControllerTest` (13) | Standalone MockMvc; the use case interfaces are small hand-written lambdas that record the commands they receive. No Spring context, no database. | Routing and JSON shapes: responses list every scope (`NONE` where never granted); a level a scope does not accept is 400 on create and patch; unknown scope or level names are 400; PATCH sends only the listed scopes and leaves omitted fields null in the command; GET never exposes a secret; cancel returns 204 and sends its command; the internal confirm/reject endpoints carry the revision and reject defaults to `REJECTED`. |
| `EventOutboxEntityTest` (4) | Plain object test | Each projection command (create, update, delete) maps to the right outbox row; the secret hash is only on created; permissions are stored as a JSON object keyed by scope. |
| `ApiKeysAdapterTest` (4) | Plain object test with mocked repositories | Saving or deleting a key writes its events to the outbox and drains them; no events writes nothing; a failing outbox write surfaces so the transaction rolls back. |
| `JpaMappingTest` (5) | Builds Hibernate metadata offline (no database) | Permissions are scope-to-level rows in the `access` schema for both the key and the pending change; no per-scope columns are left; the outbox keeps permissions as one text column and has no expiry; every entity table is in schema `access`. This checks the annotations only, not behaviour. |
| `V1CredentialTest` (5) | Pure Java | Issued credentials carry the `V1_` prefix and a hash of the secret; every credential is different; the verifier accepts the right secret and rejects others; a malformed stored hash never matches (returns false instead of throwing). |
| `ApiKeyConfigTest` (3) | Pure Java | New credentials use the configured key format; matching is case-insensitive; a format nobody can issue fails fast at startup. |

### `ArchitectureTest` (14)

ArchUnit reads the compiled production classes and fails the build if a layer imports something it should not.
The rules enforced:

1. `domain` (including `domain.repositories`) depends only on itself, `shared`, `java.*`, `jakarta.persistence`, Lombok, the UUID generator and JSpecify.
2. `domain` has no Spring dependency.
3. `shared` depends only on itself, `java.*` and Lombok (no JPA, Spring or JSON).
4. `application` never depends on `presentation` or `infrastructure`.
5. The four layers only point inwards (ArchUnit layered architecture): `presentation` and `infrastructure` are used by no layer, `application` only by those two, `domain` only by `application`, `presentation` and `infrastructure`.
6. Use case interfaces (in `application.usecase..`) are implemented only inside `application.usecase..`.
7. `presentation` does not depend on `infrastructure`, `domain.repositories` or `application.interfaces`.
8. `infrastructure` does not depend on `presentation` or `application.usecase..` (the handlers).
9. `shared` never depends on `access`.
10. No field injection (`@Autowired`, `@Inject`).
11. `*Handler` classes in `application.usecase..` are not public.
12. No package cycles among the four layers inside `access`.
13. User use cases (`usecase.user..`) and service use cases (`usecase.service..`) never depend on each other.
14. The user API controller (`ApiKeyController`) only calls user use cases; the internal controller and the facade only call service use cases.

## Integration tests (41)

All of these run the real application against a throwaway Postgres 17. Each test starts with empty Access tables
(`TRUNCATE`) and a clock that can be pinned.

### Helpers (not tests themselves)

| Class | Role |
|---|---|
| `PostgresIntegrationTest` | Base class: `@SpringBootTest`, tagged `integration`, starts the `postgres:17` Testcontainers container once per JVM and points the datasource at it. Pins the JVM time zone to `Asia/Kolkata` (same as `BackendApplication.main`) because the JDBC driver and Postgres disagree about the alias `Asia/Calcutta`. |
| `AccessIntegrationTest` | Base for the Access tests: wires every use case, a `JdbcTemplate`, MockMvc and the test clock; helpers `issue`, `issueActive`, `view`, `count` and `outbox()` (reads the rows of the outbox table). |
| `TestClockConfig` + `MutableClock` | Replace the application `Clock` with one tests can pin to a moment or move forward, so expiry can be tested without waiting. |
| `access/presentation/api/controllers/VerifyProbeController` | Test-only endpoint `GET /test-probe/verify`. Nothing in the app exposes verification over HTTP, so this lets tests prove that verification and parsing failures become 401 and 400 instead of 500. It lives in the test sources only. |

### Test classes

| Class (tests) | What it proves |
|---|---|
| `BackendApplicationTests` (1) | The whole Spring context starts against the container: bean wiring, entity mappings, the schema. |
| `AccessSchemaIntegrationTest` (4) | With the app's normal `ddl-auto=update` config: schema `access` exists and nothing was created in `public`; every table has exactly the expected columns; per-scope permission columns and the event expiry column are gone; unique and foreign-key constraints exist. |
| `ApiKeyLifecycleIntegrationTest` (17) | The whole key lifecycle through the use case interfaces with a real database: issue (provisioning key, one `apiKey.projection.create` row, secret never stored), confirm (active, permissions stored, pending rows gone), verify (success, and unknown/provisioning/wrong-secret/expired all fail identically), update (pending update, `apiKey.projection.update` at revision 2, confirmed state unchanged until confirm, then applied), no-op update (no event, no revision), refused changes (pending, stale revision, provisioning), reject then dismiss (the compensating update goes out at reject; dismiss emits nothing and leaves no rows), cancel (`CANCELLED_BY_USER`, compensating update at revision 3, dismiss emits nothing), delete then confirm (`DELETED` tombstone, dismiss removes everything with no new event), rejected delete (a restoring `apiKey.projection.create` at revision 3), rejected creation (`apiKey.projection.delete` at revision 2 at once, dismiss removes the key and emits nothing), dismissing a healthy key is refused, ownership. |
| `ApiKeyAtomicityIntegrationTest` (4) | A database trigger refuses outbox rows for a key named `boom`, so the outbox write really fails. Then: a failed issue leaves no key, pending change or permission rows; a failed update leaves the key at its old revision; a failed delete leaves it active; a failing outbox write while rejecting a creation keeps the change pending. This is the "key change and outbox row commit together or not at all" guarantee. |
| `ApiKeyConcurrencyIntegrationTest` (5, one test repeated 5 times) | 8 threads update the same key at once. Exactly one succeeds; the other 7 get `ApiKeyUpdateFailure` (HTTP 409); the key ends at revision 2 with one pending change and one `apiKey.projection.update` outbox row whose content matches the winning change. |
| `ApiKeyHttpIntegrationTest` (6) | Full stack over MockMvc: create, read, confirm, patch, conflict, reject, dismiss, delete, tombstone; 404 for unknown and foreign keys and on the internal endpoints; 409 for changing a key whose creation is pending; 400 for a level a scope does not accept, unknown scope, unknown level and malformed body; 400 for a create without name or expiry (not a 500); 401 and 400 from verification failures through the test probe. |
| `SqlMigrationValidateIntegrationTest` (4) | Applies only the hand-written `V1__create_access_schema.sql` to a fresh database, then boots with `ddl-auto=validate` so Hibernate refuses to start on any mismatch. Also checks every time column is `timestamp with time zone`, that an `Instant` round-trips unchanged under a different session time zone, and that a full key lifecycle runs on the hand-written tables. |

## What the tests do NOT prove

- **The app has never been started with `BackendApplication.main` against the dev database.** The tests start the
  Spring context through `@SpringBootTest` on a container, which is close but not the same entry point.
- **`emitted_at` is only ever null.** No relay reads the outbox and publishes rows, so nothing fills it in. Tests
  check that it stays null, not that publishing works.
- **The concurrency test does not say why a loser lost.** It accepts any `ApiKeyUpdateFailure`, which can come from
  the optimistic-lock retry running out or from the state check ("pending changes") on a retried attempt. Either
  way the outcome (one winner, no corruption) is what is asserted.
- **Verification over real HTTP is only proven through a test-only probe.** The production app has no HTTP verify
  endpoint; `ApiKeyFacade` is in-process. The 401/400 mapping is tested via `VerifyProbeController`.
- **Key expiry is not propagated to the data plane.** Events carry no expiry and no expiry event exists (a
  deliberate decision). Tests cover expiry inside Access only.
- **Access to the internal confirm/reject endpoints is not secured or tested.** They are meant to sit behind the
  network or gateway; no authentication exists yet (the owner `userId` comes from the URL path).
- **`JpaMappingTest` is offline.** It validates annotations; the integration tests are what exercise the database.
- **No performance or load testing** beyond the 8-thread update race.

## Reading a failing test

| Symptom | Look at |
|---|---|
| `ApiKeyTest` or a handler test fails | The rule in `access/domain/aggregates/apikey/ApiKey.java`. Handlers only load, call and save. |
| `ApiKeyProjectionCommandContractTest` fails | Someone changed an event field, name, order or type string. That is a public contract change: confirm it is intended, then update the test and `access-context.md`. |
| `ArchitectureTest` names a class | A wrong import crossed a layer. Move the dependency behind an abstraction in `application` instead of loosening the rule. |
| `ScopeTest` or `PermissionsTest` fails after adding a scope | The new constant in `Scope` allows no valid level or omits `NONE`. |
| `ApiKeyControllerTest` fails | Routing, JSON shape or exception-to-status mapping in `presentation/api/controllers`. |
| `JpaMappingTest` fails | An entity annotation or table/column name changed. Also update the SQL file. |
| `SqlMigrationValidateIntegrationTest` fails | The mappings and `V1__create_access_schema.sql` disagree. Fix whichever side is wrong. |
| `AccessSchemaIntegrationTest` fails | What `ddl-auto=update` generates differs from the expected tables and columns. |
| `ApiKeyAtomicityIntegrationTest` fails | The outbox write is no longer part of the key's transaction (`ApiKeysAdapter` no longer writes the outbox row in the caller's transaction). |
| An integration test fails to start | Is Docker running? The error will mention Testcontainers or the container image. They never need `localhost:5432`. |
| Time-dependent failures | The test clock (`MutableClock`) is pinned or not reset; `AccessIntegrationTest` resets it before each test. |
