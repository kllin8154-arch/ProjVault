ALTER TABLE pkc_project
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT NULL;

UPDATE pkc_project
SET owner_user_id = (
    SELECT id FROM pkc_rbac_user
    WHERE username IN ('admin', 'local-admin')
    ORDER BY CASE WHEN username = 'admin' THEN 0 ELSE 1 END
    LIMIT 1
)
WHERE owner_user_id IS NULL;

CREATE INDEX idx_pkc_project_owner_user_id ON pkc_project(owner_user_id);
