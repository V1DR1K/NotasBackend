CREATE TABLE central_auth_users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_central_auth_users_active_username
    ON central_auth_users (username) WHERE deleted_at IS NULL;

CREATE TABLE central_auth_refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES central_auth_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    client_app VARCHAR(20),
    CONSTRAINT ck_central_auth_refresh_client_app
        CHECK (client_app IS NULL OR client_app IN ('notes', 'whatplan', 'scalegrams'))
);

CREATE INDEX idx_central_auth_refresh_user_id ON central_auth_refresh_tokens (user_id);
CREATE INDEX idx_central_auth_refresh_expiry ON central_auth_refresh_tokens (expires_at);

CREATE TABLE central_user_app_access (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES central_auth_users(id),
    app_code VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_central_user_app_access UNIQUE (user_id, app_code),
    CONSTRAINT ck_central_user_app_access_app
        CHECK (app_code IN ('notes', 'whatplan', 'scalegrams')),
    CONSTRAINT ck_central_user_app_access_role
        CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX idx_central_user_app_access_enabled
    ON central_user_app_access (app_code, enabled, role);
