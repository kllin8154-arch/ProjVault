ALTER TABLE pkc_generated_artifact
    ADD COLUMN deleted_at DATETIME NULL,
    ADD COLUMN original_relative_path VARCHAR(1000) NULL;

CREATE INDEX idx_artifact_project_deleted
    ON pkc_generated_artifact(project_id, deleted_at);

CREATE TABLE pkc_artifact_folder (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    relative_path VARCHAR(1000) NOT NULL,
    description VARCHAR(1000) NULL,
    default_folder BIT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_artifact_folder_path UNIQUE (project_id, relative_path)
);

CREATE INDEX idx_artifact_folder_project
    ON pkc_artifact_folder(project_id);
