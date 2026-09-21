CREATE INDEX ix_tasks_owner_due_date_all
    ON tasks(owner_id, due_date ASC)
    WHERE deleted_at IS NULL AND due_date IS NOT NULL;
