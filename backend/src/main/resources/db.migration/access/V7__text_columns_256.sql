-- Every text column is VARCHAR(256), the size the event contract allows for text. Widening never loses data.
ALTER TABLE access.api_key_permissions
    ALTER COLUMN scope TYPE VARCHAR(256),
    ALTER COLUMN access_level TYPE VARCHAR(256);

ALTER TABLE access.outbox
    ALTER COLUMN event_type TYPE VARCHAR(256);

ALTER TABLE access.outbox_dead_letter
    ALTER COLUMN event_type TYPE VARCHAR(256);
