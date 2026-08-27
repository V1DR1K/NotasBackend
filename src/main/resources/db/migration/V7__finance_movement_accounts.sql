ALTER TABLE finance_movements ADD COLUMN account_code VARCHAR(80);
ALTER TABLE finance_movements ADD COLUMN balance_applied BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE finance_movements
SET account_code = 'mercadopago'
WHERE account_code IS NULL;

UPDATE finance_movements
SET item_code = 'supermercado'
WHERE lower(item_code) = 'supermercado_golosineria';

UPDATE finance_movements
SET item_code = 'transferencia'
WHERE lower(item_code) IN ('inversion_pesos', 'inversion_cripto');

ALTER TABLE finance_movements ALTER COLUMN account_code SET NOT NULL;
CREATE INDEX ix_finance_owner_account_date ON finance_movements(owner_id, account_code, date DESC) WHERE deleted_at IS NULL;

UPDATE finance_accounts
SET label = 'Inversión en Pesos'
WHERE lower(code) = 'inversiones_pesos' AND deleted_at IS NULL;

UPDATE finance_accounts
SET label = 'Inversión Cripto'
WHERE lower(code) = 'crypto' AND deleted_at IS NULL;

UPDATE config_options
SET code = 'supermercado', label = 'Supermercado', sort_order = 4
WHERE kind = 'FINANCE_ITEM' AND lower(code) = 'supermercado_golosineria' AND deleted_at IS NULL;

UPDATE config_options
SET deleted_at = now(), active = false
WHERE kind = 'FINANCE_ITEM' AND lower(code) = 'inversion_cripto' AND deleted_at IS NULL;

UPDATE config_options old_item
SET code = 'transferencia', label = 'Transferencia', sort_order = 7
WHERE old_item.kind = 'FINANCE_ITEM'
  AND lower(old_item.code) = 'inversion_pesos'
  AND old_item.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM config_options existing
      WHERE existing.owner_id = old_item.owner_id
        AND existing.kind = 'FINANCE_ITEM'
        AND lower(existing.code) = 'transferencia'
        AND existing.deleted_at IS NULL
  );

UPDATE config_options
SET deleted_at = now(), active = false
WHERE kind = 'FINANCE_ITEM' AND lower(code) IN ('inversion_pesos', 'inversion_cripto') AND deleted_at IS NULL;

UPDATE config_options
SET label = 'Sueldo', sort_order = 0, active = true
WHERE kind = 'FINANCE_ITEM' AND lower(code) = 'sueldo' AND deleted_at IS NULL;

UPDATE config_options
SET label = 'Pedidos Ya', sort_order = 2, active = true
WHERE kind = 'FINANCE_ITEM' AND lower(code) = 'pedidos_ya' AND deleted_at IS NULL;

UPDATE config_options
SET label = 'Comida Afuera', sort_order = 3, active = true
WHERE kind = 'FINANCE_ITEM' AND lower(code) = 'comida_afuera' AND deleted_at IS NULL;

UPDATE config_options
SET label = 'Nafta', sort_order = 5, active = true
WHERE kind = 'FINANCE_ITEM' AND lower(code) = 'nafta' AND deleted_at IS NULL;

UPDATE config_options
SET label = 'Uber/Didi', sort_order = 6, active = true
WHERE kind = 'FINANCE_ITEM' AND lower(code) = 'uber_didi' AND deleted_at IS NULL;

INSERT INTO config_options (owner_id, kind, code, label, sort_order, active)
SELECT users.id, 'FINANCE_ITEM', 'otro', 'Otro', 1, true
FROM app_users users
WHERE NOT EXISTS (
    SELECT 1 FROM config_options item
    WHERE item.owner_id = users.id AND item.kind = 'FINANCE_ITEM' AND lower(item.code) = 'otro' AND item.deleted_at IS NULL
);

INSERT INTO config_options (owner_id, kind, code, label, sort_order, active)
SELECT users.id, 'FINANCE_ITEM', 'transferencia', 'Transferencia', 7, true
FROM app_users users
WHERE NOT EXISTS (
    SELECT 1 FROM config_options item
    WHERE item.owner_id = users.id AND item.kind = 'FINANCE_ITEM' AND lower(item.code) = 'transferencia' AND item.deleted_at IS NULL
);
