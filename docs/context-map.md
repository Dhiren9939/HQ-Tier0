# HQ Context Map

HQ is a webhook delivery system. This document lists its bounded contexts and how they relate. Existing diagrams: [Architecture-Diagram.excalidraw](Architecture-Diagram.excalidraw) and [Service-Diagram.excalidraw](Service-Diagram.excalidraw).

The backend is a modular monolith that will be split later. Each context below is a module with a published contract (its events); nothing reaches into another context's internals.

## Contexts

| Context | Responsibility | Subdomain |
|---|---|---|
| Identity | Authentication and users (Auth + User merged). | Generic |
| Access | Issues and manages API keys (formerly "API Key"). See [access-class-diagram.md](access-class-diagram.md). | Supporting |
| Subscriptions | Two aggregates: **Tenant** (the user's customer, like a Svix "application") and **Channel** (endpoint URL + subscribed event types + webhook signing secret). | Supporting |
| Ingestion | Authenticates callers via its Access projection, writes the event to the DB, emits an event for the Dispatcher. No routing. See [ingestion-class-diagram.md](ingestion-class-diagram.md). | Supporting |
| Dispatcher | Owns fan-out/routing, retry policy, scheduling, delivery status (formerly "Delivery Service"). | Core |
| Delivery Worker | Executes HTTP attempts, signs requests at send time. Mostly stateless, separate deployable. | Core |
| Monitoring | Reads all event streams and models whatever it needs. | Supporting |

## Planes

- **Control plane**: Identity, Access, Subscriptions. They own configuration and publish control events through an outbox.
- **Data plane**: Ingestion, Dispatcher, Delivery Worker. They keep local projections of control data and conform to the published events.
- **Monitoring** sits beside both and only reads.

## Context map

```
   CONTROL PLANE                                   DATA PLANE
 +-----------+
 | Identity  |
 +-----------+
 +-----------+  apiKey.* (PL, outbox)   +-----------+
 |  Access   | -----------------------> | Ingestion |
 |           |                          +-----+-----+
 +-----------+                                | event-ingested
 +---------------+  channel events (PL)       |
 | Subscriptions | ------------------+        v
 | Tenant,Channel| <-- ack (only ----+--> +------------+
 +---------------+   Dispatcher acks)     | Dispatcher |
                                          +--+------^--+
                          delivery-requested |      | attempt-completed
                                             v      |
                                          +----------+--+
                                          |  Delivery   |
                                          |   Worker    |
                                          +------+------+
                                                 |
        Dispatcher's channel projection  <-------+ read-only (Shared Kernel)
        (ChannelTarget)

 +------------+  reads ALL event streams (Conformist, downstream)
 | Monitoring | <--- every arrow above
 +------------+
```

## Relationships

**Published Language (control plane to data plane).** Identity, Access and Subscriptions write control events to an outbox. Consumers build projections from them. The control plane is the source of truth and does not wait for answers: consumers conform to the published events.

**Access to Ingestion (Conformist).** Ingestion consumes Access events (`apiKey.projection.create/update/delete`) into its own projection and authenticates from that. Access applies every change immediately; the data plane must conform, there is no confirm/reject handshake. Each event carries a per-key `version` (the key's revision, unique and strictly increasing) for ordering and idempotency. **Every event is a full snapshot of the key** at that version (`accessId`, `accessSecretHash`, name, permissions, `createdAt`, `expiresAt`, ...); the event type only says why it was sent. A consumer keeps the highest `version` it has seen per key and ignores lower ones, so a missed event never needs a resync with Access: the next event is enough to rebuild the key, and the data plane stays independent of Access's availability. Ingestion keeps a deleted key as a tombstone with its version, so a late replay cannot bring it back, and a deleted key never comes back (see [ingestion-class-diagram.md](ingestion-class-diagram.md)). **Outbox:** Access stores each event in `access.outbox` as metadata columns (`event_id`, `event_type`, `api_key_id`, `change_version`, `occurred_at`, `emitted_at`, `attempts` and `last_error` for diagnosis) plus the ready-to-publish JSON body in one `payload jsonb` column, so adding an event or a field never changes the table. The body is JSON with `eventId`, `eventType` and the integration event's fields, and `emittedAt`, which is added when the event is published. The wire contract has no nulls except `expiresAt` (null means the key never expires); `permissions` is an object of scope to level where a missing scope means `NONE` and `{}` means no access; scopes and levels are published by fixed wire names (`ApiKeyScope.wireName()`), so renaming a Java constant never changes the wire. JSON key order is not part of the contract. The golden files in `backend/src/test/resources/contracts/apikey.projection/` are the contract both sides test against. **Transport:** the outbox relay publishes at least once to the RabbitMQ super stream `apikey.projections` (2 partitions), routed by `apiKeyId` so each key's events stay on one partition in order, with application properties `eventType`, `apiKeyId` and `version` for filtering without parsing. Every 2 seconds it takes up to 100 pending rows in order of occurrence and publishes them with publisher confirms. A broker outage cancels the batch and the next tick sends it again, so expect duplicates (keep the highest `version`, or dedupe on `eventId`). There are no retries and no dead letter: a row the broker refuses, or whose stored payload breaks the contract, halts the relay until a person fixes the cause and restarts it. There is no expiry event or scheduler, so enforcing expiry in the projection is the data plane's choice (Access enforces it in its own verification either way).

**Subscriptions to Dispatcher.** The Dispatcher consumes channel events and Ingestion events. It is the only consumer that acknowledges channel events.

**Dispatcher to Delivery Worker.** The Dispatcher emits `delivery-requested` events. The Worker executes the attempt and reports back with `attempt-completed` events.

**Shared Kernel: channel projection (owned by Dispatcher).** The Worker is a separate deployable but reads the Dispatcher's channel projection read-only.
- Shape is deliberately narrow: `ChannelTarget { channelId, endpointUrl, signingSecrets[], version, deleted }`.
- Access goes through the port `ChannelTargetLookup`. Now: a read-only JDBC adapter. Later: an event-driven projection with encrypted secrets.
- The Worker signs with all active secrets, at send time.
- If the channel is deleted, the Worker reports outcome `CHANNEL_REMOVED` to the Dispatcher instead of sending.

**Monitoring (Conformist).** Monitoring consumes every stream and conforms to whatever shape publishers emit; it keeps its own model. Consequence: every event stream is a public contract, so event names, fields and type strings change only deliberately.

## Subdomains

| Type | Contexts | Why |
|---|---|---|
| Core | Dispatcher, Delivery Worker | Reliable delivery is the product. |
| Supporting | Access, Subscriptions, Ingestion, Monitoring | Necessary, tailored, not differentiating. |
| Generic | Identity | Commodity; buy or use standard solutions. |

## Not contexts

API gateway, load balancers and the outbox poller are infrastructure. They carry or relay traffic and events but own no domain model.
