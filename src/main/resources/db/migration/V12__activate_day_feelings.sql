UPDATE config_options
SET active = true
WHERE kind = 'DAY_FEELING'
  AND deleted_at IS NULL;
