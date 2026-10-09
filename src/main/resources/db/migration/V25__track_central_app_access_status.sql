ALTER TABLE central_user_app_access
    ADD COLUMN access_status VARCHAR(20) NOT NULL DEFAULT 'NONE';

UPDATE central_user_app_access
SET access_status = CASE WHEN enabled THEN 'APPROVED' ELSE 'NONE' END;

-- Legacy self-registration granted Whatplan access immediately. Keep never-used
-- self-registered accounts in the new approval queue instead.
UPDATE central_user_app_access grant_row
SET enabled = FALSE,
    access_status = 'PENDING'
FROM central_auth_users user_row
WHERE grant_row.user_id = user_row.id
  AND grant_row.app_code = 'whatplan'
  AND grant_row.enabled = TRUE
  AND user_row.last_login_at IS NULL
  AND user_row.must_change_password = FALSE
  AND user_row.deleted_at IS NULL;

ALTER TABLE central_user_app_access
    ADD CONSTRAINT ck_central_user_app_access_status
        CHECK (access_status IN ('NONE', 'PENDING', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT ck_central_user_app_access_enabled_status
        CHECK (enabled = (access_status = 'APPROVED'));

CREATE INDEX idx_central_user_app_access_status
    ON central_user_app_access (app_code, access_status);
