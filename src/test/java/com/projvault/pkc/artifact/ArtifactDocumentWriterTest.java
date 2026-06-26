package com.projvault.pkc.artifact;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactDocumentWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesSearchableChinesePdf() throws Exception {
        Path output = tempDir.resolve("report.pdf");
        new ArtifactDocumentWriter().write(output, ArtifactFormat.PDF, "项目核验报告",
                "## 关键字段\n- 风险编号：RISK-LEVEL-C3\n- 复核日期：2026-09-30",
                List.of("18增量局部替换实验.md"));

        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("项目核验报告", "风险编号", "RISK-LEVEL-C3", "资料来源");
        }
    }
}
