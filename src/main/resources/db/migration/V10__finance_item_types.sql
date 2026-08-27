ALTER TABLE config_options ADD COLUMN finance_type VARCHAR(20);

UPDATE config_options
SET finance_type = 'INCOME'
WHERE kind = 'FINANCE_ITEM'
  AND lower(code) IN ('sueldo', 'otro');

UPDATE config_options
SET finance_type = 'EXPENSE'
WHERE kind = 'FINANCE_ITEM'
  AND lower(code) IN ('pedidos_ya', 'comida_afuera', 'supermercado', 'nafta', 'uber_didi');

UPDATE config_options
SET finance_type = 'TRANSFER'
WHERE kind = 'FINANCE_ITEM'
  AND lower(code) = 'transferencia';

UPDATE config_options
SET finance_type = 'EXPENSE'
WHERE kind = 'FINANCE_ITEM'
  AND finance_type IS NULL;

ALTER TABLE config_options
ADD CONSTRAINT ck_config_finance_type CHECK (
    (kind = 'FINANCE_ITEM' AND finance_type IN ('INCOME', 'EXPENSE', 'TRANSFER'))
    OR (kind <> 'FINANCE_ITEM' AND finance_type IS NULL)
);
