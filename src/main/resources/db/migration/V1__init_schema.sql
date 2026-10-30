-- Developers table
CREATE TABLE developers (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_DEVELOPER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Usage plans table
CREATE TABLE usage_plans (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    requests_per_window INT NOT NULL,
    window_seconds INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- API keys table
CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    developer_id UUID NOT NULL REFERENCES developers(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES usage_plans(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_lookup ON api_keys(key_prefix, status);
CREATE INDEX idx_api_keys_developer ON api_keys(developer_id);

-- Usage events table
CREATE TABLE usage_events (
    id UUID PRIMARY KEY,
    api_key_id UUID REFERENCES api_keys(id) ON DELETE SET NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    route VARCHAR(255) NOT NULL,
    status_code INT NOT NULL,
    latency_ms BIGINT NOT NULL,
    decision VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(50)
);

CREATE INDEX idx_usage_events_api_key_time ON usage_events(api_key_id, occurred_at);
