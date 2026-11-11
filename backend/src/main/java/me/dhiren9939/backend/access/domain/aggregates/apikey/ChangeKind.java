package me.dhiren9939.backend.access.domain.aggregates.apikey;

/** What a pending change asks the data plane to do. */
public enum ChangeKind {
    CREATE,
    UPDATE,
    DELETE
}
