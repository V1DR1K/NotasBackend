ALTER TABLE day_entries ADD COLUMN analysis_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE day_entries ALTER COLUMN status_code DROP NOT NULL;
ALTER TABLE day_entries ALTER COLUMN feeling DROP NOT NULL;

UPDATE day_entries SET status_code = 'green' WHERE status_code = 'green_enjoyed';
UPDATE day_entries SET status_code = 'yellow' WHERE status_code = 'yellow_mixed';
UPDATE day_entries SET status_code = 'red' WHERE status_code = 'red_care';

UPDATE config_options
SET deleted_at = now(), active = false
WHERE kind = 'DAY_STATUS'
  AND code IN ('green_enjoyed', 'yellow_mixed', 'red_care')
  AND deleted_at IS NULL;

UPDATE config_options
SET active = false
WHERE kind = 'DAY_FEELING'
  AND code IN (
    'enfocado', 'abrumado', 'motivado', 'aliviado', 'desanimado',
    'frustrado', 'inspirado', 'preocupado', 'orgulloso', 'descansado',
    'solo', 'sociable'
  )
  AND deleted_at IS NULL;

ALTER TABLE day_entries
ADD CONSTRAINT ck_day_entries_analysis_state CHECK (
    (analysis_status = 'PENDING' AND status_code IS NULL AND feeling IS NULL)
    OR (analysis_status = 'COMPLETED' AND status_code IS NOT NULL AND feeling IS NOT NULL)
);
