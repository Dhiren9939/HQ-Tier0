-- Outbox relay: retry bookkeeping on the outbox, a partial index for the pending scan, and the dead-letter table.
-- Dead-lettering moves a row out of access.outbox into access.outbox_dead_letter in one transaction.

ALTER TABLE access.outbox ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;
ALTER TABLE access.outbox ADD COLUMN IF NOT EXISTS last_error TEXT;

CREATE INDEX IF NOT EXISTS outbox_pending_idx
    ON access.outbox (occurred_at, change_version)
    WHERE emitted_at IS NULL;

CREATE TABLE IF NOT EXISTS access.outbox_dead_letter(
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    api_key_id UUID NOT NULL,
    change_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    user_id UUID NOT NULL,
    access_id VARCHAR(256) NOT NULL,
    access_secret_hash VARCHAR(256),
    api_key_name VARCHAR(256) NOT NULL,
    permissions TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    attempts INT NOT NULL,
    last_error TEXT,
    dead_at TIMESTAMPTZ NOT NULL
);
