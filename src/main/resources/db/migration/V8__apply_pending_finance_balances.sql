WITH pending AS (
    SELECT owner_id,
           sum(CASE WHEN bucket = 'INCOME' THEN amount_ars ELSE -amount_ars END) AS delta
    FROM finance_movements
    WHERE deleted_at IS NULL
      AND balance_applied = false
      AND lower(account_code) = 'mercadopago'
      AND bucket IN ('INCOME', 'EXPENSE')
    GROUP BY owner_id
), projected AS (
    SELECT account.id,
           round(
               account.balance_ars
               * power(
                   1 + (account.annual_rate_percent / 100 / 365),
                   greatest(0, floor(extract(epoch FROM (now() - account.balance_as_of)) / 86400)::int)
               )
               + pending.delta,
               2
           ) AS balance_ars
    FROM finance_accounts account
    JOIN pending ON pending.owner_id = account.owner_id
    WHERE lower(account.code) = 'mercadopago'
      AND account.deleted_at IS NULL
)
UPDATE finance_accounts account
SET balance_ars = projected.balance_ars,
    balance_as_of = now()
FROM projected
WHERE account.id = projected.id;

UPDATE finance_movements
SET balance_applied = true
WHERE deleted_at IS NULL
  AND balance_applied = false
  AND lower(account_code) = 'mercadopago'
  AND bucket IN ('INCOME', 'EXPENSE');
