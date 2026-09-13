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

CREATE TABLE IF NOT EXISTS api_keys (
    api_key_id   UUID PRIMARY KEY,
    api_key_hash VARCHAR(255) NOT NULL,
    is_valid     BOOLEAN NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ
);
