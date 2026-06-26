CREATE TABLE IF NOT EXISTS pkc_user_ai_setting (
    user_id BIGINT NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    model VARCHAR(200) NOT NULL,
    timeout_seconds INT NOT NULL DEFAULT 60,
    encrypted_api_key TEXT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_ai_setting_user
        FOREIGN KEY (user_id) REFERENCES pkc_rbac_user(id) ON DELETE CASCADE
);

ALTER TABLE pkc_evaluation_run
    ADD COLUMN IF NOT EXISTS requested_by_user_id BIGINT NULL;

CREATE INDEX idx_evaluation_run_requested_by
    ON pkc_evaluation_run(requested_by_user_id);
