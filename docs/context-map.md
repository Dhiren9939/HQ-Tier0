# HQ Context Map

HQ is a webhook delivery system. This document lists its bounded contexts and how they relate. Existing diagrams: [Architecture-Diagram.excalidraw](Architecture-Diagram.excalidraw) and [Service-Diagram.excalidraw](Service-Diagram.excalidraw).

The backend is a modular monolith that will be split later. Each context below is a module with a published contract (its events); nothing reaches into another context's internals.

## Contexts

| Context | Responsibility | Subdomain |
|---|---|---|
| Identity | Authentication and users (Auth + User merged). | Generic |
| Access | Issues and manages API keys (formerly "API Key"). See [access-context.md](access-context.md). | Supporting |
| Subscriptions | Two aggregates: **Tenant** (the user's customer, like a Svix "application") and **Channel** (endpoint URL + subscribed event types + webhook signing secret). | Supporting |
| Ingestion | Authenticates callers via its Access projection, writes the event to the DB, emits an event for the Dispatcher. No routing. | Supporting |
| Dispatcher | Owns fan-out/routing, retry policy, scheduling, delivery status (formerly "Delivery Service"). | Core |
| Delivery Worker | Executes HTTP attempts, signs requests at send time. Mostly stateless, separate deployable. | Core |
| Monitoring | Reads all event streams and models whatever it needs. | Supporting |

## Planes

- **Control plane**: Identity, Access, Subscriptions. They own configuration and publish control events through an outbox.
- **Data plane**: Ingestion, Dispatcher, Delivery Worker. They keep local projections of control data and acknowledge control events back.
- **Monitoring** sits beside both and only reads.

## Context map

```
   CONTROL PLANE                                   DATA PLANE
 +-----------+
 | Identity  |
 +-----------+
 +-----------+  apiKey.* (PL, outbox)   +-----------+
 |  Access   | -----------------------> | Ingestion |
 |           | <----- confirm/reject -- |           |
 +-----------+                          +-----+-----+
 +---------------+  channel events (PL)       | event-ingested
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

**Published Language (control plane to data plane).** Identity, Access and Subscriptions write control events to an outbox. Consumers build projections from them. The data plane acknowledges each control event back (confirm or reject).

**Access to Ingestion.** Ingestion consumes Access events (`apiKey.created/updated/deleted`) into its own projection and authenticates from that. It confirms or rejects each revision. The events carry no key expiry (`expiresAt` was removed) and no compensating expiry event exists, so this projection does not enforce expiry; that is deliberate, see `access-context.md`, Outbox.

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
