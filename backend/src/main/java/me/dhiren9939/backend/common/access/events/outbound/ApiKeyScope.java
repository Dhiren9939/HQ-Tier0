package me.dhiren9939.backend.common.access.events.outbound;

/** The scopes an API key can be granted, as published. A scope missing from {@code permissions} means no access. */
public enum ApiKeyScope {
    INGESTION,
    TENANT,
    CHANNEL
}
