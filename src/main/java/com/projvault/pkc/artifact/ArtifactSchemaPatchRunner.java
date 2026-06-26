package com.projvault.pkc.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
public class ArtifactSchemaPatchRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ArtifactSchemaPatchRunner.class);
    private final JdbcTemplate jdbcTemplate;

    public ArtifactSchemaPatchRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumnIfMissing("evidence_json", "TEXT DEFAULT '[]'");
        addColumnIfMissing("quality_json", "TEXT");
        addColumnIfMissing("content_text", "TEXT");
        addColumnIfMissing("quality_status", "VARCHAR(16)");
        addColumnIfMissing("parent_artifact_id", "BIGINT");
        addColumnIfMissing("root_artifact_id", "BIGINT");
        addColumnIfMissing("version_no", "INT DEFAULT 1 NOT NULL");
        addColumnIfMissing("previewed_at", "TIMESTAMP");
        addColumnIfMissing("deleted_at", "TIMESTAMP");
        addColumnIfMissing("original_relative_path", "VARCHAR(1024)");
        jdbcTemplate.update("UPDATE pkc_generated_artifact SET evidence_json = '[]' WHERE evidence_json IS NULL");
        jdbcTemplate.update("UPDATE pkc_generated_artifact SET version_no = 1 WHERE version_no IS NULL OR version_no < 1");
    }

    private void addColumnIfMissing(String column, String ddlType) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'PKC_GENERATED_ARTIFACT'
                  AND UPPER(COLUMN_NAME) = UPPER(?)
                """, Integer.class, column);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE pkc_generated_artifact ADD COLUMN " + column + " " + ddlType);
        log.info("[schema] added pkc_generated_artifact.{}", column);
    }
}
