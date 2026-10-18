CREATE TABLE api_key.api_keys(
    api_key_id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    user_id UUID NOT NULL,
    access_id VARCHAR(256) NOT NULL UNIQUE,
    access_secret_hash VARCHAR(256) NOT NULL,
    api_key_name VARCHAR(256) NOT NULL,

    ingestion_permission VARCHAR(50) NOT NULL,
    tenant_permission VARCHAR(50) NOT NULL,
    channel_permission VARCHAR(50) NOT NULL,

    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP
);

CREATE TABLE api_key.outbox(
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    api_key_id UUID NOT NULL,
    user_id UUID NOT NULL,
    access_id VARCHAR(256) NOT NULL,
    access_secret_hash VARCHAR(256),
    api_key_name VARCHAR(256) NOT NULL,

    ingestion_permission VARCHAR(50) NOT NULL,
    tenant_permission VARCHAR(50) NOT NULL,
    channel_permission VARCHAR(50) NOT NULL,

    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP
);