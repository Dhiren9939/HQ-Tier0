CREATE SCHEMA IF NOT EXISTS access;

CREATE TABLE access.pending_changes(
    change_id UUID PRIMARY KEY,
    lock_version BIGINT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    kind VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(50),

    -- attributes the key will have once this change is confirmed
    api_key_name VARCHAR(256) NOT NULL,
    expires_at TIMESTAMPTZ
);

-- scope -> level rows; a scope without a row means NONE
CREATE TABLE access.pending_change_permissions(
    change_id UUID NOT NULL REFERENCES access.pending_changes(change_id),
    scope VARCHAR(50) NOT NULL,
    access_level VARCHAR(50) NOT NULL,
    PRIMARY KEY (change_id, scope)
);

CREATE TABLE access.api_keys(
    api_key_id UUID PRIMARY KEY,
    lock_version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    access_id VARCHAR(256) NOT NULL UNIQUE,
    access_secret_hash VARCHAR(256) NOT NULL,
    api_key_name VARCHAR(256) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    pending_change_id UUID UNIQUE REFERENCES access.pending_changes(change_id),
    revision BIGINT NOT NULL,
    confirmed_revision BIGINT NOT NULL
);

-- scope -> level rows; a scope without a row means NONE
CREATE TABLE access.api_key_permissions(
    api_key_id UUID NOT NULL REFERENCES access.api_keys(api_key_id),
    scope VARCHAR(50) NOT NULL,
    access_level VARCHAR(50) NOT NULL,
    PRIMARY KEY (api_key_id, scope)
);

CREATE TABLE access.outbox(
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    api_key_id UUID NOT NULL,
    change_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    emitted_at TIMESTAMPTZ,
    user_id UUID NOT NULL,
    access_id VARCHAR(256) NOT NULL,
    access_secret_hash VARCHAR(256),
    api_key_name VARCHAR(256) NOT NULL,

    -- JSON object keyed by scope name, e.g. {"INGESTION":"WRITE"}; absent scope = NONE
    permissions TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ
);
