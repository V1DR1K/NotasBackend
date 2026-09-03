CREATE TABLE crypto_investments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    date DATE NOT NULL,
    asset_code VARCHAR(20) NOT NULL CHECK(asset_code IN ('BTCUSDT', 'SOLUSDT', 'ETHUSDT', 'PEPEUSDT')),
    amount_usd NUMERIC(19, 8) NOT NULL CHECK(amount_usd > 0),
    amount_ars NUMERIC(19, 2) NOT NULL CHECK(amount_ars > 0),
    exchange_rate_snapshot NUMERIC(19, 8) NOT NULL CHECK(exchange_rate_snapshot > 0),
    source_key VARCHAR(80),
    note VARCHAR(1000),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_crypto_investments_owner_date ON crypto_investments(owner_id, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_crypto_investments_owner_asset ON crypto_investments(owner_id, asset_code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_crypto_investments_source_key ON crypto_investments(owner_id, source_key) WHERE source_key IS NOT NULL;

INSERT INTO crypto_investments (owner_id, date, asset_code, amount_usd, amount_ars, exchange_rate_snapshot, source_key, note)
SELECT users.id, CURRENT_DATE, seed.asset_code, seed.amount_usd, seed.amount_ars, 1504.230395, seed.source_key, 'Compra inicial cargada desde el saldo cripto existente'
FROM app_users users
CROSS JOIN (VALUES
    ('BTCUSDT', 1800.00000000::numeric, 2707614.71::numeric, 'legacy-tomas-btcusdt'),
    ('SOLUSDT', 800.00000000::numeric, 1203384.32::numeric, 'legacy-tomas-solusdt'),
    ('ETHUSDT', 1200.00000000::numeric, 1805076.47::numeric, 'legacy-tomas-ethusdt'),
    ('PEPEUSDT', 326.00000000::numeric, 490379.11::numeric, 'legacy-tomas-pepeusdt')
) AS seed(asset_code, amount_usd, amount_ars, source_key)
WHERE lower(users.username) = 'tomas'
  AND NOT EXISTS (
      SELECT 1 FROM crypto_investments existing
      WHERE existing.owner_id = users.id AND existing.source_key = seed.source_key
  );
