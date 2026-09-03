CREATE TABLE calendar_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    date DATE NOT NULL,
    category_code VARCHAR(80) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_calendar_events_owner_date
    ON calendar_events(owner_id, date ASC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_calendar_events_owner_category_date
    ON calendar_events(owner_id, category_code, date ASC)
    WHERE deleted_at IS NULL;
