DELETE FROM finance_movements;
DELETE FROM config_options WHERE kind IN ('FINANCE_CONCEPT', 'FINANCE_CATEGORY');

ALTER TABLE finance_movements DROP COLUMN concept_code;
ALTER TABLE finance_movements DROP COLUMN category_code;
ALTER TABLE finance_movements ADD COLUMN item_code VARCHAR(80) NOT NULL;

CREATE INDEX ix_finance_owner_item ON finance_movements(owner_id, item_code) WHERE deleted_at IS NULL;
