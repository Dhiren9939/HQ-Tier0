package me.dhiren9939.backend.ingestion.domain.projection;

/** Whether the key still exists in Access. A created or updated key is ACTIVE; a deleted key is DELETED for good. */
public enum KeyStatus {
    ACTIVE,
    DELETED
}
