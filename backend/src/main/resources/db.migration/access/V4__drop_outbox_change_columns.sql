-- The outbox now carries projection commands only. The change-kind and failure-reason columns belonged to the
-- old apiKey.change.* events (compensation/cancel) and are dead; IF EXISTS keeps this safe on fresh databases,
-- which never created them.

ALTER TABLE access.outbox DROP COLUMN IF EXISTS change_kind;
ALTER TABLE access.outbox DROP COLUMN IF EXISTS failure_reason;
