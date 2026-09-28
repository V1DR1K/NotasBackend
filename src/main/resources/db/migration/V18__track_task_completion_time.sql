ALTER TABLE tasks ADD COLUMN completed_at TIMESTAMPTZ;

UPDATE tasks
SET completed_at = updated_at
WHERE status = 'COMPLETED';

CREATE INDEX ix_tasks_owner_completed_at
    ON tasks(owner_id, completed_at DESC)
    WHERE deleted_at IS NULL AND status = 'COMPLETED';
