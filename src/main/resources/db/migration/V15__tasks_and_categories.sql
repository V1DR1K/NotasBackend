CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    title VARCHAR(180) NOT NULL,
    detail TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    category_code VARCHAR(80) NOT NULL,
    due_date DATE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_tasks_owner_status_updated
    ON tasks(owner_id, status, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_tasks_owner_due_date
    ON tasks(owner_id, due_date ASC)
    WHERE deleted_at IS NULL AND status <> 'COMPLETED';

CREATE INDEX ix_tasks_owner_category_status
    ON tasks(owner_id, category_code, status)
    WHERE deleted_at IS NULL;
