package com.projvault.pkc.artifact;

import com.projvault.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactContentValidatorTest {

    private final ArtifactContentValidator validator = new ArtifactContentValidator();

    @Test
    void acceptsCompleteSqlSchema() {
        String sql = """
                CREATE TABLE project_file (
                    id BIGINT NOT NULL,
                    project_id BIGINT NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_project_file (project_id, id)
                );
                """;

        assertThatCode(() -> validator.validate(ArtifactFormat.SQL, sql))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTruncatedSqlBeforeFileIsWritten() {
        String sql = """
                CREATE TABLE project_file (
                    id BIGINT NOT NULL,
                    project_id BIGINT NOT NULL,
                    PRIMARY KEY (id),
                    content_hash VARCHAR(64)
                """;

        assertThatThrownBy(() -> validator.validate(ArtifactFormat.SQL, sql))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("截断");
    }
}
