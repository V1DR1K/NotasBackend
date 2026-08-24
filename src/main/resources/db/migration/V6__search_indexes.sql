CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_notes_title_trgm_active ON notes USING gin (lower(title) gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX ix_notes_body_trgm_active ON notes USING gin (lower(body) gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX ix_day_entries_description_trgm_active ON day_entries USING gin (lower(description) gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX ix_finance_movements_note_trgm_active ON finance_movements USING gin (lower(note) gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX ix_files_name_trgm_active ON files USING gin (lower(name) gin_trgm_ops) WHERE deleted_at IS NULL;
