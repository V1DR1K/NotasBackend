CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE semantic_search_queue (
    id BIGSERIAL PRIMARY KEY,
    owner_id UUID NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_id UUID NOT NULL,
    title TEXT NOT NULL,
    detail TEXT NOT NULL,
    content TEXT NOT NULL,
    record_date DATE NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 0,
    claimed_at TIMESTAMPTZ,
    UNIQUE (owner_id, source_type, source_id)
);

CREATE INDEX ix_semantic_search_queue_ready
    ON semantic_search_queue(next_attempt_at, requested_at)
    WHERE claimed_at IS NULL;

CREATE TABLE semantic_search_documents (
    id BIGSERIAL PRIMARY KEY,
    owner_id UUID NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_id UUID NOT NULL,
    chunk_number INTEGER NOT NULL,
    title TEXT NOT NULL,
    detail TEXT NOT NULL,
    record_date DATE NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768) NOT NULL,
    UNIQUE (owner_id, source_type, source_id, chunk_number)
);

CREATE INDEX ix_semantic_search_documents_owner_source
    ON semantic_search_documents(owner_id, source_type, source_id);
CREATE INDEX ix_semantic_search_documents_embedding
    ON semantic_search_documents USING hnsw (embedding vector_cosine_ops);

CREATE FUNCTION queue_semantic_search_change() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    row_data JSONB;
    owner_value UUID;
    source_value UUID;
    deleted_value TIMESTAMPTZ;
    title_value TEXT;
    detail_value TEXT;
    content_value TEXT;
    date_value DATE;
    source_value_type TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        row_data := to_jsonb(OLD);
    ELSE
        row_data := to_jsonb(NEW);
    END IF;

    owner_value := (row_data ->> 'owner_id')::UUID;
    source_value := (row_data ->> 'id')::UUID;
    deleted_value := NULLIF(row_data ->> 'deleted_at', '')::TIMESTAMPTZ;
    source_value_type := CASE TG_TABLE_NAME
        WHEN 'notes' THEN 'notes'
        WHEN 'day_entries' THEN 'day'
        WHEN 'finance_movements' THEN 'finances'
        WHEN 'files' THEN 'files'
        WHEN 'tasks' THEN 'tasks'
        WHEN 'calendar_events' THEN 'calendar'
    END;

    IF TG_OP = 'DELETE' OR deleted_value IS NOT NULL THEN
        DELETE FROM semantic_search_documents
        WHERE owner_id = owner_value AND source_type = source_value_type AND source_id = source_value;
        DELETE FROM semantic_search_queue
        WHERE owner_id = owner_value AND source_type = source_value_type AND source_id = source_value;
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    title_value := COALESCE(
        row_data ->> 'title',
        row_data ->> 'feeling',
        row_data ->> 'item_code',
        row_data ->> 'name',
        row_data ->> 'description',
        ''
    );

    detail_value := concat_ws(' ',
        NULLIF(row_data ->> 'body', ''),
        NULLIF(row_data ->> 'description', ''),
        NULLIF(row_data ->> 'detail', ''),
        NULLIF(row_data ->> 'note', ''),
        NULLIF(row_data ->> 'status_code', ''),
        NULLIF(row_data ->> 'category_code', ''),
        NULLIF(row_data ->> 'project_code', ''),
        NULLIF(row_data ->> 'bucket', ''),
        NULLIF(row_data ->> 'account_code', ''),
        NULLIF(row_data ->> 'extension', ''),
        NULLIF(row_data ->> 'mime_type', ''),
        NULLIF(row_data ->> 'kind', ''),
        NULLIF(row_data ->> 'status', '')
    );
    content_value := concat_ws(' ', NULLIF(title_value, ''), NULLIF(detail_value, ''));
    date_value := COALESCE(
        NULLIF(row_data ->> 'date', '')::DATE,
        NULLIF(row_data ->> 'due_date', '')::DATE,
        COALESCE(NULLIF(row_data ->> 'created_at', '')::TIMESTAMPTZ, now())::DATE
    );

    DELETE FROM semantic_search_documents
    WHERE owner_id = owner_value AND source_type = source_value_type AND source_id = source_value;

    INSERT INTO semantic_search_queue(owner_id, source_type, source_id, title, detail, content, record_date, content_hash)
    VALUES (
        owner_value,
        source_value_type,
        source_value,
        title_value,
        detail_value,
        content_value,
        date_value,
        md5(content_value)
    )
    ON CONFLICT (owner_id, source_type, source_id) DO UPDATE SET
        title = EXCLUDED.title,
        detail = EXCLUDED.detail,
        content = EXCLUDED.content,
        record_date = EXCLUDED.record_date,
        content_hash = EXCLUDED.content_hash,
        requested_at = now(),
        next_attempt_at = now(),
        attempts = 0,
        claimed_at = NULL;

    RETURN NEW;
END;
$$;

CREATE TRIGGER semantic_search_notes AFTER INSERT OR UPDATE OR DELETE ON notes
    FOR EACH ROW EXECUTE FUNCTION queue_semantic_search_change();
CREATE TRIGGER semantic_search_days AFTER INSERT OR UPDATE OR DELETE ON day_entries
    FOR EACH ROW EXECUTE FUNCTION queue_semantic_search_change();
CREATE TRIGGER semantic_search_movements AFTER INSERT OR UPDATE OR DELETE ON finance_movements
    FOR EACH ROW EXECUTE FUNCTION queue_semantic_search_change();
CREATE TRIGGER semantic_search_files AFTER INSERT OR UPDATE OR DELETE ON files
    FOR EACH ROW EXECUTE FUNCTION queue_semantic_search_change();
CREATE TRIGGER semantic_search_tasks AFTER INSERT OR UPDATE OR DELETE ON tasks
    FOR EACH ROW EXECUTE FUNCTION queue_semantic_search_change();
CREATE TRIGGER semantic_search_calendar AFTER INSERT OR UPDATE OR DELETE ON calendar_events
    FOR EACH ROW EXECUTE FUNCTION queue_semantic_search_change();

INSERT INTO semantic_search_queue(owner_id, source_type, source_id, title, detail, content, record_date, content_hash)
SELECT owner_id, 'notes', id, title,
       concat_ws(' ', body, category_code, project_code),
       concat_ws(' ', title, body, category_code, project_code),
       date, md5(concat_ws(' ', title, body, category_code, project_code))
FROM notes WHERE deleted_at IS NULL
UNION ALL
SELECT owner_id, 'day', id, COALESCE(feeling, ''),
       concat_ws(' ', description, status_code),
       concat_ws(' ', feeling, description, status_code),
       date, md5(concat_ws(' ', feeling, description, status_code))
FROM day_entries WHERE deleted_at IS NULL
UNION ALL
SELECT owner_id, 'finances', id, item_code,
       concat_ws(' ', note, bucket, account_code),
       concat_ws(' ', item_code, note, bucket, account_code),
       date, md5(concat_ws(' ', item_code, note, bucket, account_code))
FROM finance_movements WHERE deleted_at IS NULL
UNION ALL
SELECT owner_id, 'files', id, name,
       concat_ws(' ', description, extension, mime_type, kind, project_code),
       concat_ws(' ', name, description, extension, mime_type, kind, project_code),
       created_at::DATE, md5(concat_ws(' ', name, description, extension, mime_type, kind, project_code))
FROM files WHERE deleted_at IS NULL
UNION ALL
SELECT owner_id, 'tasks', id, title,
       concat_ws(' ', detail, status, category_code, project_code),
       concat_ws(' ', title, detail, status, category_code, project_code),
       COALESCE(due_date, created_at::DATE),
       md5(concat_ws(' ', title, detail, status, category_code, project_code))
FROM tasks WHERE deleted_at IS NULL
UNION ALL
SELECT owner_id, 'calendar', id, description,
       concat_ws(' ', category_code, project_code),
       concat_ws(' ', description, category_code, project_code),
       date, md5(concat_ws(' ', description, category_code, project_code))
FROM calendar_events WHERE deleted_at IS NULL;
