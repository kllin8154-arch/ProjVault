package com.projvault.pkc.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactDocumentWriterPdfTest {
    @TempDir Path tempDir;

    @Test
    void writesChinesePdfWithDiscoveredPlatformFont() throws Exception {
        Path target = tempDir.resolve("项目报告.pdf");
        new ArtifactDocumentWriter().write(target, ArtifactFormat.PDF, "项目周报",
                "## 风险\n当前风险需要复核。", List.of("风险清单.xlsx"));

        assertThat(target).isRegularFile();
        assertThat(Files.readAllBytes(target)).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        assertThat(Files.size(target)).isGreaterThan(2_000);
    }
}
