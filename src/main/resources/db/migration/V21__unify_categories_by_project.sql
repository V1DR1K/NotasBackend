-- Categories become shared by tasks, notes and calendar events, scoped to a project.
-- Build one target category for each owner/project/code/label combination represented
-- by the previous per-feature configuration and its records.
ALTER TABLE config_options ADD COLUMN project_code VARCHAR(80);

DROP INDEX uq_config_owner_kind_code;

CREATE TEMP TABLE legacy_category_projects ON COMMIT DROP AS
SELECT c.id AS source_id,
       c.owner_id,
       c.code,
       c.label,
       c.emoji,
       c.sort_order,
       c.active,
       c.deleted_at,
       c.created_at,
       c.updated_at,
       COALESCE(projects.project_code,
                CASE lower(c.code)
                    WHEN 'facultad' THEN 'facultad'
                    WHEN 'laburo' THEN 'laburo'
                    ELSE 'personal'
                END) AS project_code
FROM config_options c
LEFT JOIN LATERAL (
    SELECT r.project_code
    FROM (
        SELECT t.project_code, t.deleted_at
        FROM tasks t
        WHERE c.kind = 'TASK_CATEGORY' AND t.owner_id = c.owner_id
          AND lower(t.category_code) = lower(c.code)
        UNION ALL
        SELECT n.project_code, n.deleted_at
        FROM notes n
        WHERE c.kind = 'NOTE_CATEGORY' AND n.owner_id = c.owner_id
          AND lower(n.category_code) = lower(c.code)
        UNION ALL
        SELECT e.project_code, e.deleted_at
        FROM calendar_events e
        WHERE c.kind = 'EVENT_CATEGORY' AND e.owner_id = c.owner_id
          AND lower(e.category_code) = lower(c.code)
    ) r
    GROUP BY r.project_code
    ORDER BY bool_or(r.deleted_at IS NULL) DESC, r.project_code
) projects ON TRUE
WHERE c.kind IN ('TASK_CATEGORY', 'NOTE_CATEGORY', 'EVENT_CATEGORY');

-- A code cannot identify two differently named categories in the same project
-- because records store category_code. Stop before rewriting anything if that
-- ambiguity exists; the migration must be given an explicit mapping first.
DO $$
BEGIN
    IF EXISTS (
        SELECT owner_id, lower(project_code), lower(code)
        FROM legacy_category_projects
        GROUP BY owner_id, lower(project_code), lower(code)
        HAVING count(DISTINCT lower(btrim(label))) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot unify categories: a project has one category code with different labels';
    END IF;
END $$;

INSERT INTO config_options (
    id, owner_id, kind, code, label, emoji, sort_order, active, deleted_at,
    created_at, updated_at, version, project_code
)
SELECT gen_random_uuid(), owner_id, 'CATEGORY', min(code), min(label), min(emoji),
       min(sort_order), bool_or(active),
       CASE WHEN bool_or(deleted_at IS NULL) THEN NULL ELSE min(deleted_at) END,
       min(created_at), max(updated_at), 0, project_code
FROM legacy_category_projects
GROUP BY owner_id, lower(project_code), lower(code), lower(btrim(label)), project_code;

-- Rewrite stored labels only when case/whitespace variants of an otherwise
-- identical code and project were merged. Project assignments and record data
-- stay intact.
UPDATE tasks t
SET category_code = c.code
FROM config_options c
WHERE c.kind = 'CATEGORY' AND c.owner_id = t.owner_id
  AND lower(c.project_code) = lower(t.project_code)
  AND lower(c.code) = lower(t.category_code) AND t.deleted_at IS NULL;
UPDATE notes n
SET category_code = c.code
FROM config_options c
WHERE c.kind = 'CATEGORY' AND c.owner_id = n.owner_id
  AND lower(c.project_code) = lower(n.project_code)
  AND lower(c.code) = lower(n.category_code) AND n.deleted_at IS NULL;
UPDATE calendar_events e
SET category_code = c.code
FROM config_options c
WHERE c.kind = 'CATEGORY' AND c.owner_id = e.owner_id
  AND lower(c.project_code) = lower(e.project_code)
  AND lower(c.code) = lower(e.category_code) AND e.deleted_at IS NULL;

DELETE FROM config_options WHERE kind IN ('TASK_CATEGORY', 'NOTE_CATEGORY', 'EVENT_CATEGORY');

ALTER TABLE config_options ADD CONSTRAINT ck_config_category_project
    CHECK ((kind = 'CATEGORY' AND project_code IS NOT NULL) OR (kind <> 'CATEGORY' AND project_code IS NULL));

CREATE UNIQUE INDEX uq_config_owner_kind_code
    ON config_options(owner_id, kind, lower(code))
    WHERE deleted_at IS NULL AND kind <> 'CATEGORY';
CREATE UNIQUE INDEX uq_config_owner_project_category_code
    ON config_options(owner_id, lower(project_code), lower(code))
    WHERE deleted_at IS NULL AND kind = 'CATEGORY';
