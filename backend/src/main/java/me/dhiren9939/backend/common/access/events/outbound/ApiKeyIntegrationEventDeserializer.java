package me.dhiren9939.backend.common.access.events.outbound;

/**
 * Reads a published apiKey.projection message body back into its event. Contract only: the implementation lives with
 * whoever reads the stream.
 */
public interface ApiKeyIntegrationEventDeserializer {

    /**
     * The event in a published body, with {@link ApiKeyIntegrationEvent#emittedAt()} set.
     *
     * @throws ApiKeyEventDeserializationException if the body breaks the published contract, naming the field
     */
    ApiKeyIntegrationEvent deserialize(String body);
}
