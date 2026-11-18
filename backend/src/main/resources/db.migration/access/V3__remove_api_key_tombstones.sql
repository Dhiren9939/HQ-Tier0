-- Deleting a key now removes it outright; there are no tombstones left to dismiss.

ALTER TABLE access.api_keys DROP COLUMN deleted_at;
