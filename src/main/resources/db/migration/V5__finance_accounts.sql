CREATE TABLE finance_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    code VARCHAR(80) NOT NULL,
    label VARCHAR(160) NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK(account_type IN ('CASH', 'INVESTMENT', 'CRYPTO')),
    balance_ars NUMERIC(19, 2) NOT NULL CHECK(balance_ars >= 0),
    annual_rate_percent NUMERIC(9, 4) NOT NULL DEFAULT 0 CHECK(annual_rate_percent >= 0),
    growth_mode VARCHAR(30) NOT NULL CHECK(growth_mode IN ('DAILY_TNA', 'MANUAL')),
    balance_as_of TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_finance_account_owner_code_active ON finance_accounts(owner_id, lower(code)) WHERE deleted_at IS NULL;
CREATE INDEX ix_finance_account_owner_type ON finance_accounts(owner_id, account_type) WHERE deleted_at IS NULL AND active = true;
