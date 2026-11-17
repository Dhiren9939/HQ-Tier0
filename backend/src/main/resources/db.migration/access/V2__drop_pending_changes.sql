-- The confirm/reject handshake with the data plane is gone: HQ applies every change at once
-- and the data plane conforms to the published events. No pending changes are tracked.

ALTER TABLE access.api_keys DROP COLUMN pending_change_id;
ALTER TABLE access.api_keys DROP COLUMN confirmed_revision;

DROP TABLE access.pending_change_permissions;
DROP TABLE access.pending_changes;
