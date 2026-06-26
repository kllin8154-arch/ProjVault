package com.projvault.pkc.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
public class FileAssetSchemaPatchRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FileAssetSchemaPatchRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public FileAssetSchemaPatchRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumnIfMissing("relevance_status", "VARCHAR(32)");
        addColumnIfMissing("relevance_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("relevance_reason", "VARCHAR(512)");
        addColumnIfMissing("scope_type", "VARCHAR(64)");
        addColumnIfMissing("scope_reason", "VARCHAR(512)");
        addColumnIfMissing("content_signature", "VARCHAR(64)");
    }

    private void addColumnIfMissing(String column, String ddlType) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'PKC_FILE'
                  AND UPPER(COLUMN_NAME) = UPPER(?)
                """, Integer.class, column);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE pkc_file ADD COLUMN " + column + " " + ddlType);
        log.info("[schema] added pkc_file.{}", column);
    }
}
