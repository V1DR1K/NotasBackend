CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE config_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    kind VARCHAR(30) NOT NULL,
    code VARCHAR(80) NOT NULL,
    label VARCHAR(160) NOT NULL,
    emoji VARCHAR(16),
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_config_owner_kind_code ON config_options(owner_id, kind, lower(code)) WHERE deleted_at IS NULL;
CREATE INDEX ix_config_owner_kind_active ON config_options(owner_id, kind, active) WHERE deleted_at IS NULL;

CREATE TABLE day_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    date DATE NOT NULL,
    status_code VARCHAR(80) NOT NULL,
    feeling VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_day_owner_date_active ON day_entries(owner_id, date) WHERE deleted_at IS NULL;
CREATE INDEX ix_day_owner_date ON day_entries(owner_id, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_day_owner_status_date ON day_entries(owner_id, status_code, date DESC) WHERE deleted_at IS NULL;

CREATE TABLE notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    category_code VARCHAR(80) NOT NULL,
    date DATE NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_notes_owner_date ON notes(owner_id, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_notes_owner_category_date ON notes(owner_id, category_code, date DESC) WHERE deleted_at IS NULL;

CREATE TABLE finance_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    date DATE NOT NULL,
    bucket VARCHAR(20) NOT NULL CHECK(bucket IN ('INCOME', 'EXPENSE', 'INVESTED')),
    concept_code VARCHAR(80) NOT NULL,
    category_code VARCHAR(80) NOT NULL,
    amount_ars NUMERIC(19, 2) NOT NULL CHECK(amount_ars > 0),
    exchange_rate_snapshot NUMERIC(19, 8) NOT NULL CHECK(exchange_rate_snapshot > 0),
    note VARCHAR(1000),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_finance_owner_date ON finance_movements(owner_id, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_finance_owner_bucket_date ON finance_movements(owner_id, bucket, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_finance_owner_concept ON finance_movements(owner_id, concept_code) WHERE deleted_at IS NULL;
CREATE INDEX ix_finance_owner_category ON finance_movements(owner_id, category_code) WHERE deleted_at IS NULL;

CREATE TABLE exchange_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    buy NUMERIC(19, 8) NOT NULL CHECK(buy > 0),
    sell NUMERIC(19, 8) NOT NULL CHECK(sell > 0),
    fetched_at TIMESTAMPTZ NOT NULL,
    source VARCHAR(40) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_exchange_owner_currency UNIQUE(owner_id, currency)
);
CREATE INDEX ix_exchange_owner_currency ON exchange_rates(owner_id, currency);

CREATE TABLE file_folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    name VARCHAR(120) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_folder_owner_name_active ON file_folders(owner_id, lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX ix_folder_owner_name ON file_folders(owner_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    folder_id UUID REFERENCES file_folders(id),
    name VARCHAR(255) NOT NULL,
    extension VARCHAR(32) NOT NULL,
    mime_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK(size_bytes >= 0),
    kind VARCHAR(20) NOT NULL,
    storage_key UUID NOT NULL UNIQUE,
    checksum VARCHAR(128) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_files_owner_uploaded ON files(owner_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_files_owner_folder ON files(owner_id, folder_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_files_owner_kind ON files(owner_id, kind) WHERE deleted_at IS NULL;

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID REFERENCES app_users(id),
    actor_id UUID REFERENCES app_users(id),
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB
);
CREATE INDEX ix_audit_owner_occurred ON audit_events(owner_id, occurred_at DESC);
