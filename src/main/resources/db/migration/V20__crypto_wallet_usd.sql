-- Additive: retain every recorded ARS balance, lot, sale and movement.
ALTER TABLE finance_accounts ADD COLUMN balance_usd NUMERIC(19,8) CHECK (balance_usd >= 0);
ALTER TABLE finance_accounts ADD COLUMN usd_balance_estimated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE finance_movements ADD COLUMN crypto_amount_usd NUMERIC(19,8) CHECK (crypto_amount_usd > 0);
ALTER TABLE finance_movements ADD COLUMN crypto_book_amount_ars NUMERIC(19,2) CHECK (crypto_book_amount_ars >= 0);

-- Historical P2P rates were not collected. Estimate only the unallocated cash using
-- the latest stored snapshot; users can correct the USD total without changing history.
WITH lots AS (
    SELECT i.owner_id, SUM(i.amount_usd - COALESCE(s.usd,0)) AS usd,
           SUM(i.amount_ars - COALESCE(s.ars,0)) AS ars
    FROM crypto_investments i LEFT JOIN (
        SELECT investment_id, SUM(cost_basis_usd) usd, SUM(cost_basis_ars) ars
        FROM crypto_sales WHERE deleted_at IS NULL GROUP BY investment_id
    ) s ON s.investment_id=i.id WHERE i.deleted_at IS NULL GROUP BY i.owner_id
), snapshots AS (
    SELECT owner_id, exchange_rate_snapshot rate, date, created_at FROM crypto_investments
    UNION ALL
    SELECT owner_id, exchange_rate_snapshot, date, created_at FROM finance_movements WHERE account_code='crypto'
), latest AS (
    SELECT DISTINCT ON(owner_id) owner_id, rate FROM snapshots ORDER BY owner_id,date DESC,created_at DESC
)
UPDATE finance_accounts a SET balance_usd=ROUND(COALESCE(l.usd,0) + GREATEST(a.balance_ars-COALESCE(l.ars,0),0)/r.rate,8),
    usd_balance_estimated=TRUE
FROM latest r LEFT JOIN lots l ON l.owner_id=r.owner_id
WHERE a.owner_id=r.owner_id AND a.account_type='CRYPTO';
UPDATE finance_accounts SET balance_usd=0 WHERE account_type='CRYPTO' AND balance_ars=0 AND balance_usd IS NULL;
