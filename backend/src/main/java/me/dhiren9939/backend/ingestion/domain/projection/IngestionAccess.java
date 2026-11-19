package me.dhiren9939.backend.ingestion.domain.projection;

/** What a key may do in Ingestion. A key without the INGESTION scope has NONE. */
public enum IngestionAccess {
    NONE,
    WRITE
}
