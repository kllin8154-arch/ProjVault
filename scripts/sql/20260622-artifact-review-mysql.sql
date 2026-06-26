-- ProjVault AI artifact review migration for MySQL 8.
-- Back up the database before applying this script in production.

ALTER TABLE pkc_generated_artifact
    ADD COLUMN evidence_json LONGTEXT NULL,
    ADD COLUMN quality_json LONGTEXT NULL,
    ADD COLUMN content_text LONGTEXT NULL,
    ADD COLUMN quality_status VARCHAR(16) NULL,
    ADD COLUMN parent_artifact_id BIGINT NULL,
    ADD COLUMN root_artifact_id BIGINT NULL,
    ADD COLUMN version_no INT NULL DEFAULT 1,
    ADD COLUMN previewed_at DATETIME NULL;

UPDATE pkc_generated_artifact
SET evidence_json = '[]'
WHERE evidence_json IS NULL;

UPDATE pkc_generated_artifact
SET version_no = 1
WHERE version_no IS NULL OR version_no < 1;

ALTER TABLE pkc_generated_artifact
    MODIFY evidence_json LONGTEXT NOT NULL,
    MODIFY version_no INT NOT NULL DEFAULT 1;

CREATE INDEX idx_artifact_root_version
    ON pkc_generated_artifact(project_id, root_artifact_id, version_no);

CREATE INDEX idx_artifact_parent
    ON pkc_generated_artifact(parent_artifact_id);
