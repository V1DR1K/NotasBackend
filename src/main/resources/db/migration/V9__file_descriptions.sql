ALTER TABLE files ADD COLUMN description VARCHAR(255);

UPDATE files
SET description = name
WHERE description IS NULL;

ALTER TABLE files ALTER COLUMN description SET NOT NULL;

CREATE INDEX ix_files_description_trgm_active
    ON files USING gin (lower(description) gin_trgm_ops)
    WHERE deleted_at IS NULL;
