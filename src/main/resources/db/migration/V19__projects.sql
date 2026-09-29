-- Existing rows keep their IDs and content. Project assignments use existing categories
-- where they are unambiguous; everything else remains available under Personal.
INSERT INTO config_options (owner_id, kind, code, label, sort_order, active)
SELECT u.id, 'PROJECT', p.code, p.label, p.sort_order, true
FROM app_users u
CROSS JOIN (VALUES ('personal', 'Personal', 0), ('facultad', 'Facultad', 1), ('laburo', 'Laburo', 2)) AS p(code, label, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM config_options c
    WHERE c.owner_id = u.id AND c.kind = 'PROJECT' AND lower(c.code) = p.code AND c.deleted_at IS NULL
);

ALTER TABLE tasks ADD COLUMN project_code VARCHAR(80) NOT NULL DEFAULT 'personal';
ALTER TABLE notes ADD COLUMN project_code VARCHAR(80) NOT NULL DEFAULT 'personal';
ALTER TABLE files ADD COLUMN project_code VARCHAR(80) NOT NULL DEFAULT 'personal';
ALTER TABLE file_folders ADD COLUMN project_code VARCHAR(80) NOT NULL DEFAULT 'personal';
ALTER TABLE calendar_events ADD COLUMN project_code VARCHAR(80) NOT NULL DEFAULT 'personal';

UPDATE tasks SET project_code = lower(category_code)
WHERE lower(category_code) IN ('facultad', 'laburo');
UPDATE notes SET project_code = CASE WHEN lower(category_code) = 'work' THEN 'laburo' ELSE 'facultad' END
WHERE lower(category_code) IN ('work', 'facultad');
UPDATE calendar_events SET project_code = lower(category_code)
WHERE lower(category_code) IN ('facultad', 'laburo');
UPDATE file_folders SET project_code = lower(name)
WHERE lower(name) IN ('facultad', 'laburo');
UPDATE files f SET project_code = folder.project_code
FROM file_folders folder
WHERE f.folder_id = folder.id AND f.owner_id = folder.owner_id;

CREATE INDEX ix_tasks_owner_project ON tasks(owner_id, project_code) WHERE deleted_at IS NULL;
CREATE INDEX ix_notes_owner_project_date ON notes(owner_id, project_code, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_files_owner_project ON files(owner_id, project_code) WHERE deleted_at IS NULL;
CREATE INDEX ix_folders_owner_project ON file_folders(owner_id, project_code) WHERE deleted_at IS NULL;
CREATE INDEX ix_events_owner_project_date ON calendar_events(owner_id, project_code, date) WHERE deleted_at IS NULL;
