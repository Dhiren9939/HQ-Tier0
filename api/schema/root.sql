-- Manually applied schema (spring.jpa.hibernate.ddl-auto=none).
-- Keep this in sync with the JPA entities by hand; re-run the relevant
-- CREATE/ALTER statements against maindb after changing an entity.

CREATE TABLE IF NOT EXISTS users (
    user_id     UUID PRIMARY KEY,
    google_sub  VARCHAR(255) NOT NULL,
    first_name  VARCHAR(255),
    last_name   VARCHAR(255),
    email       VARCHAR(255),
    CONSTRAINT uq_users_google_sub UNIQUE (google_sub)
);

CREATE TABLE IF NOT EXISTS refresh_sessions (
    session_id          UUID PRIMARY KEY,
    user_id              UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    refresh_token_hash   VARCHAR(255) NOT NULL,
    device_info          VARCHAR(255),
    issued_at            TIMESTAMPTZ NOT NULL,
    expires_at           TIMESTAMPTZ NOT NULL,
    revoked_at           TIMESTAMPTZ,
    replaced_by          UUID,
    last_used_at         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_refresh_session_user_id ON refresh_sessions (user_id);

-- No enabled/disabled flag: a key is considered enabled exactly while it hasn't expired
-- (expires_at is null or in the future) - that's derived, not stored.
CREATE TABLE IF NOT EXISTS api_keys (
    api_key_id   UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_api_keys_user_id ON api_keys (user_id);

-- If api_keys already existed without these columns/with is_valid (it was created before this
-- change), apply manually against the existing DB instead of relying on CREATE TABLE IF NOT
-- EXISTS above:
-- ALTER TABLE api_keys ADD COLUMN user_id UUID REFERENCES users (user_id) ON DELETE CASCADE;
-- ALTER TABLE api_keys ADD COLUMN name VARCHAR(255);
-- -- backfill user_id/name for any existing rows here, then:
-- ALTER TABLE api_keys ALTER COLUMN user_id SET NOT NULL;
-- ALTER TABLE api_keys ALTER COLUMN name SET NOT NULL;
-- CREATE INDEX IF NOT EXISTS idx_api_keys_user_id ON api_keys (user_id);
-- ALTER TABLE api_keys DROP COLUMN IF EXISTS is_valid;

CREATE TABLE IF NOT EXISTS tenants (
    tenant_id  UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tenants_user_id ON tenants (user_id);

CREATE TABLE IF NOT EXISTS channels (
    channel_id     UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
    call_back_url  VARCHAR(2048) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_channels_tenant_id ON channels (tenant_id);

CREATE TABLE IF NOT EXISTS channel_event_types (
    channel_id  UUID NOT NULL REFERENCES channels (channel_id) ON DELETE CASCADE,
    event_type  VARCHAR(255) NOT NULL,
    tenant_id   UUID NOT NULL,
    PRIMARY KEY (channel_id, event_type)
);

CREATE INDEX IF NOT EXISTS idx_channel_event_types_tenant_id ON channel_event_types (tenant_id);
