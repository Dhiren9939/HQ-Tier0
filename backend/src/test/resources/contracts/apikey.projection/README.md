# apiKey.projection golden files

The published contract of the `apikey.projections` super stream, as checked-in examples. They are the shape both
sides test against: Access's serializer must write exactly these bodies (`ApiKeyProjectionGoldenContractTest`), and
consumers must read them (`JsonApiKeyIntegrationEventDeserializerTest` reads `create.published.json`).

- `create.json`, `update.json`, `delete.json`: the stored outbox body of each event type (no `emittedAt`).
- `create.published.json`: what goes on the stream: the stored body plus `emittedAt`.

Changing a file here is a contract change: every consumer has to agree first. Key order is not part of the
contract.
