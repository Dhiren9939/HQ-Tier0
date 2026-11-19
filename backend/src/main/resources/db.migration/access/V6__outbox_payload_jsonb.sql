-- The outbox keeps only metadata in columns; the event is a ready-to-publish JSON document in `payload`, so new
-- events and fields need no schema change. No backfill: rows written before this had no payload, so they are
-- cleared (every event is a full snapshot of its key, so the key's next event carries its whole state).
-- Apply by hand before starting a build that includes this change.

DELETE FROM access.outbox;
DELETE FROM access.outbox_dead_letter;

ALTER TABLE access.outbox
    ADD COLUMN payload JSONB NOT NULL,
    DROP COLUMN user_id,
    DROP COLUMN access_id,
    DROP COLUMN access_secret_hash,
    DROP COLUMN api_key_name,
    DROP COLUMN permissions,
    DROP COLUMN created_at,
    DROP COLUMN expires_at;

ALTER TABLE access.outbox_dead_letter
    ADD COLUMN payload JSONB NOT NULL,
    DROP COLUMN user_id,
    DROP COLUMN access_id,
    DROP COLUMN access_secret_hash,
    DROP COLUMN api_key_name,
    DROP COLUMN permissions,
    DROP COLUMN created_at,
    DROP COLUMN expires_at;
