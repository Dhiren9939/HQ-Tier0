package me.dhiren9939.backend.shared.access.events.domain;

/** What a pending change asks the data plane to do. */
public enum ChangeKind {
    CREATE,
    UPDATE,
    DELETE
}
