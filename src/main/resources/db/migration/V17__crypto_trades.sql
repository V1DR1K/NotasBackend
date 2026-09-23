ALTER TABLE crypto_investments
    ADD COLUMN unit_price_usd NUMERIC(28, 12),
    ADD COLUMN quantity NUMERIC(28, 18),
    ADD CONSTRAINT ck_crypto_investments_unit_price CHECK (unit_price_usd IS NULL OR unit_price_usd > 0),
    ADD CONSTRAINT ck_crypto_investments_quantity CHECK (quantity IS NULL OR quantity > 0);

CREATE TABLE crypto_sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    investment_id UUID NOT NULL REFERENCES crypto_investments(id),
    date DATE NOT NULL,
    quantity NUMERIC(28, 18) NOT NULL CHECK (quantity > 0),
    proceeds_usd NUMERIC(19, 8) NOT NULL CHECK (proceeds_usd > 0),
    unit_price_usd NUMERIC(28, 12) NOT NULL CHECK (unit_price_usd > 0),
    cost_basis_usd NUMERIC(19, 8) NOT NULL CHECK (cost_basis_usd > 0),
    cost_basis_ars NUMERIC(19, 2) NOT NULL CHECK (cost_basis_ars >= 0),
    exchange_rate_snapshot NUMERIC(19, 8) NOT NULL CHECK (exchange_rate_snapshot > 0),
    note VARCHAR(1000),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_crypto_sales_investment_date ON crypto_sales(investment_id, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_crypto_sales_owner_date ON crypto_sales(owner_id, date DESC);
