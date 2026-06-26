ALTER TABLE pkc_scan_task
    ADD COLUMN total_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN large_file_count INT NOT NULL DEFAULT 0;

CREATE TABLE pkc_golden_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    question VARCHAR(1000) NOT NULL,
    expected_keywords TEXT NOT NULL,
    expected_source_patterns TEXT NULL,
    mode VARCHAR(16) NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_golden_project (project_id)
);

CREATE TABLE pkc_evaluation_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_questions INT NOT NULL DEFAULT 0,
    passed_questions INT NOT NULL DEFAULT 0,
    average_score DOUBLE NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    details_json TEXT NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    PRIMARY KEY (id),
    INDEX idx_eval_project (project_id, id)
);

CREATE TABLE pkc_rbac_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rbac_role_code UNIQUE (code)
);

CREATE TABLE pkc_rbac_role_permission (
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES pkc_rbac_role(id)
);

CREATE TABLE pkc_rbac_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    password_salt VARCHAR(64) NOT NULL,
    enabled BIT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rbac_user_username UNIQUE (username)
);

CREATE TABLE pkc_rbac_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES pkc_rbac_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES pkc_rbac_role(id)
);

CREATE TABLE pkc_permission_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    username VARCHAR(64) NULL,
    permission_code VARCHAR(128) NOT NULL,
    method VARCHAR(16) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    allowed BIT NOT NULL,
    remote_address VARCHAR(64) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_permission_audit_created (created_at),
    INDEX idx_permission_audit_user (user_id)
);
