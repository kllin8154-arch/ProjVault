package com.projvault.pkc.artifact;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactPreviewServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void convertsDocxToSafeHtmlPreview() throws Exception {
        Path file = tempDir.resolve("report.docx");
        new ArtifactDocumentWriter().write(file, ArtifactFormat.DOCX,
                "项目报告", "# 结论\n- 已完成审查", List.of("source.md"));
        ArtifactPreviewService service = serviceFor(artifact(1L, "DOCX", "report.docx"), file);
        ArtifactPreviewDTO preview = service.preview(1L);
        byte[] pdf = service.renderPdfPreview(1L);

        assertThat(preview.previewKind()).isEqualTo("HTML");
        assertThat(preview.content()).contains("结论", "已完成审查");
        assertThat(preview.editableContent()).contains("结论");
        assertThat(pdf).startsWith((byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46);
    }

    @Test
    void rendersPptxThumbnailAndReportsPageCount() throws Exception {
        Path file = tempDir.resolve("report.pptx");
        new ArtifactDocumentWriter().write(file, ArtifactFormat.PPTX,
                "项目汇报", "## 进展\n- 完成第一阶段", List.of("source.md"));
        ArtifactPreviewService service = serviceFor(artifact(2L, "PPTX", "report.pptx"), file);

        ArtifactPreviewDTO preview = service.preview(2L);
        byte[] image = service.renderPptxPage(2L, 0);

        assertThat(preview.previewKind()).isEqualTo("SLIDES");
        assertThat(preview.pageCount()).isGreaterThanOrEqualTo(2);
        assertThat(image).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }

    @Test
    void exposesPdfAsPagedPreview() throws Exception {
        Path file = tempDir.resolve("report.pdf");
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            pdf.addPage(new PDPage());
            pdf.save(file.toFile());
        }
        ArtifactPreviewDTO preview = serviceFor(artifact(3L, "PDF", "report.pdf"), file).preview(3L);

        assertThat(preview.previewKind()).isEqualTo("PDF");
        assertThat(preview.pageCount()).isEqualTo(2);
        assertThat(preview.inlineUrl()).endsWith("/3/inline");
    }

    private ArtifactPreviewService serviceFor(GeneratedArtifact artifact, Path path) {
        ArtifactService artifacts = mock(ArtifactService.class);
        GeneratedArtifactRepository repository = mock(GeneratedArtifactRepository.class);
        when(artifacts.getEntity(artifact.getId())).thenReturn(artifact);
        when(artifacts.resolveArtifactPath(artifact)).thenReturn(path);
        when(artifacts.toDto(artifact)).thenReturn(null);
        when(artifacts.sourceFilesOf(artifact)).thenReturn(List.of("source.md"));
        when(repository.save(any(GeneratedArtifact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new ArtifactPreviewService(artifacts, repository,
                new ArtifactFileValidator(), new ArtifactQualityService(),
                new ArtifactDocumentWriter());
    }

    private GeneratedArtifact artifact(Long id, String format, String relativePath) {
        GeneratedArtifact artifact = new GeneratedArtifact();
        artifact.setId(id);
        artifact.setProjectId(1L);
        artifact.setArtifactType("PROJECT_REPORT");
        artifact.setFormat(format);
        artifact.setTitle("预览测试");
        artifact.setRelativePath(relativePath);
        artifact.setSourceFilesJson("[]");
        artifact.setEvidenceJson("[]");
        return artifact;
    }
}
