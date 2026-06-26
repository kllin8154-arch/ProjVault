package com.projvault.pkc.artifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projvault.common.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class ArtifactPreviewService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_EDITABLE_CHARS = 500_000;

    private final ArtifactService artifactService;
    private final GeneratedArtifactRepository artifactRepository;
    private final ArtifactFileValidator fileValidator;
    private final ArtifactQualityService qualityService;
    private final ArtifactDocumentWriter documentWriter;

    public ArtifactPreviewService(ArtifactService artifactService,
                                  GeneratedArtifactRepository artifactRepository,
                                  ArtifactFileValidator fileValidator,
                                  ArtifactQualityService qualityService,
                                  ArtifactDocumentWriter documentWriter) {
        this.artifactService = artifactService;
        this.artifactRepository = artifactRepository;
        this.fileValidator = fileValidator;
        this.qualityService = qualityService;
        this.documentWriter = documentWriter;
    }

    @Transactional
    public ArtifactPreviewDTO preview(Long artifactId) {
        GeneratedArtifact artifact = artifactService.getEntity(artifactId);
        Path path = artifactService.resolveArtifactPath(artifact);
        ArtifactFormat format = formatOf(artifact);
        List<ArtifactEvidenceDTO> evidence = evidenceOf(artifact);
        String editable = extractEditableText(artifact, path, format);
        ArtifactQualityDTO quality = ensureQuality(artifact, path, format, editable, evidence);

        String previewKind;
        String content = editable;
        int pageCount = 1;
        switch (format) {
            case MARKDOWN -> previewKind = "MARKDOWN";
            case SQL -> previewKind = "CODE";
            case HTML -> {
                previewKind = "HTML";
                content = safeHtml(FilesRead.readString(path));
            }
            case DOCX -> {
                previewKind = "HTML";
                content = docxToHtml(path);
            }
            case PPTX -> {
                previewKind = "SLIDES";
                content = "";
                pageCount = pptxPageCount(path);
            }
            case PDF -> {
                previewKind = "PDF";
                content = "";
                pageCount = pdfPageCount(path);
            }
            default -> throw new BusinessException(422, "不支持预览该交付物格式");
        }
        return new ArtifactPreviewDTO(
                artifactService.toDto(artifact), previewKind, content, editable,
                "/api/pkc/artifacts/" + artifactId + "/inline", pageCount, evidence, quality);
    }

    @Transactional
    public GeneratedArtifactDTO acknowledgePreview(Long artifactId) {
        GeneratedArtifact artifact = artifactService.getEntity(artifactId);
        if (artifact.getQualityStatus() == null) {
            preview(artifactId);
            artifact = artifactService.getEntity(artifactId);
        }
        artifact.setPreviewedAt(java.time.LocalDateTime.now());
        return artifactService.toDto(artifactRepository.save(artifact));
    }

    public byte[] renderPptxPage(Long artifactId, int pageIndex) {
        GeneratedArtifact artifact = artifactService.getEntity(artifactId);
        if (formatOf(artifact) != ArtifactFormat.PPTX) {
            throw new BusinessException(422, "该交付物不是 PowerPoint");
        }
        Path path = artifactService.resolveArtifactPath(artifact);
        try (InputStream input = Files.newInputStream(path); XMLSlideShow ppt = new XMLSlideShow(input)) {
            if (pageIndex < 0 || pageIndex >= ppt.getSlides().size()) {
                throw new BusinessException(404, "幻灯片页码不存在");
            }
            Dimension size = ppt.getPageSize();
            int width = 1280;
            int height = Math.max(1, (int) Math.round(width * size.getHeight() / size.getWidth()));
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setTransform(AffineTransform.getScaleInstance(
                        width / size.getWidth(), height / size.getHeight()));
                ppt.getSlides().get(pageIndex).draw(graphics);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(422, "PowerPoint 页面渲染失败: " + e.getMessage());
        }
    }

    public byte[] renderPdfPreview(Long artifactId) {
        GeneratedArtifact artifact = artifactService.getEntity(artifactId);
        ArtifactFormat format = formatOf(artifact);
        Path source = artifactService.resolveArtifactPath(artifact);
        if (format == ArtifactFormat.PDF) {
            try {
                return Files.readAllBytes(source);
            } catch (Exception e) {
                throw new BusinessException(422, "读取 PDF 失败: " + e.getMessage());
            }
        }
        if (format != ArtifactFormat.DOCX) {
            throw new BusinessException(422, "仅 Word 和 PDF 交付物支持 PDF 版式预览");
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("projvault-docx-preview-", ".pdf");
            documentWriter.write(temp, ArtifactFormat.PDF, artifact.getTitle(),
                    extractEditableText(artifact, source, format), artifactService.sourceFilesOf(artifact));
            return Files.readAllBytes(temp);
        } catch (Exception e) {
            throw new BusinessException(422, "Word 转 PDF 预览失败: " + e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception ignored) {
                    // Temporary preview cleanup does not change the response.
                }
            }
        }
    }

    public String extractEditableText(GeneratedArtifact artifact) {
        Path path = artifactService.resolveArtifactPath(artifact);
        return extractEditableText(artifact, path, formatOf(artifact));
    }

    public List<ArtifactEvidenceDTO> evidenceOf(GeneratedArtifact artifact) {
        try {
            if (artifact.getEvidenceJson() == null || artifact.getEvidenceJson().isBlank()) {
                return List.of();
            }
            return MAPPER.readValue(artifact.getEvidenceJson(), new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String extractEditableText(GeneratedArtifact artifact, Path path, ArtifactFormat format) {
        try {
            String text;
            if (artifact.getContentText() != null && !artifact.getContentText().isBlank()) {
                text = artifact.getContentText();
            } else {
                text = switch (format) {
                    case MARKDOWN, SQL, HTML -> Files.readString(path, StandardCharsets.UTF_8);
                    case DOCX -> docxToMarkdown(path);
                    case PPTX -> pptxToMarkdown(path);
                    case PDF -> pdfToText(path);
                };
            }
            return text.length() > MAX_EDITABLE_CHARS ? text.substring(0, MAX_EDITABLE_CHARS) : text;
        } catch (Exception e) {
            throw new BusinessException(422, "读取交付物正文失败: " + e.getMessage());
        }
    }

    private ArtifactQualityDTO ensureQuality(GeneratedArtifact artifact,
                                             Path path,
                                             ArtifactFormat format,
                                             String editable,
                                             List<ArtifactEvidenceDTO> evidence) {
        try {
            List<String> checks = fileValidator.validate(path, format);
            ArtifactQualityDTO quality = qualityService.evaluate(
                    artifact.getTitle(), editable, artifact.getInstructions(), evidence, checks);
            artifact.setQualityJson(MAPPER.writeValueAsString(quality));
            artifact.setQualityStatus(quality.status());
            artifactRepository.save(artifact);
            return quality;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "质量检查结果保存失败: " + e.getMessage());
        }
    }

    private String docxToHtml(Path path) {
        try (InputStream input = Files.newInputStream(path); XWPFDocument doc = new XWPFDocument(input)) {
            StringBuilder html = new StringBuilder("<article class=\"docx-preview\">");
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    String style = paragraph.getStyle() == null ? "" : paragraph.getStyle();
                    String tag = style.toLowerCase().contains("heading1") ? "h1"
                            : style.toLowerCase().contains("heading2") ? "h2"
                            : style.toLowerCase().contains("heading3") ? "h3" : "p";
                    html.append('<').append(tag).append('>').append(escapeHtml(text))
                            .append("</").append(tag).append('>');
                } else if (element instanceof XWPFTable table) {
                    html.append("<table>");
                    for (XWPFTableRow row : table.getRows()) {
                        html.append("<tr>");
                        for (XWPFTableCell cell : row.getTableCells()) {
                            html.append("<td>").append(escapeHtml(cell.getText())).append("</td>");
                        }
                        html.append("</tr>");
                    }
                    html.append("</table>");
                }
            }
            return html.append("</article>").toString();
        } catch (Exception e) {
            throw new BusinessException(422, "Word 预览转换失败: " + e.getMessage());
        }
    }

    private String docxToMarkdown(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path); XWPFDocument doc = new XWPFDocument(input)) {
            StringBuilder out = new StringBuilder();
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    String style = paragraph.getStyle() == null ? "" : paragraph.getStyle().toLowerCase();
                    String prefix = style.contains("heading1") ? "# "
                            : style.contains("heading2") ? "## "
                            : style.contains("heading3") ? "### " : "";
                    out.append(prefix).append(text).append("\n\n");
                } else if (element instanceof XWPFTable table) {
                    appendMarkdownTable(out, table);
                }
            }
            return out.toString().strip();
        }
    }

    private void appendMarkdownTable(StringBuilder out, XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return;
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<XWPFTableCell> cells = rows.get(rowIndex).getTableCells();
            out.append('|');
            for (XWPFTableCell cell : cells) {
                out.append(' ').append(cell.getText().replace("|", "\\|")).append(" |");
            }
            out.append('\n');
            if (rowIndex == 0) {
                out.append('|');
                for (int i = 0; i < cells.size(); i++) {
                    out.append(" --- |");
                }
                out.append('\n');
            }
        }
        out.append('\n');
    }

    private String pptxToMarkdown(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path); XMLSlideShow ppt = new XMLSlideShow(input)) {
            StringBuilder out = new StringBuilder();
            int index = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                out.append("## 幻灯片 ").append(index++).append('\n');
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape && !textShape.getText().isBlank()) {
                        for (String line : textShape.getText().split("\\R")) {
                            if (!line.isBlank()) {
                                out.append("- ").append(line.strip()).append('\n');
                            }
                        }
                    }
                }
                out.append('\n');
            }
            return out.toString().strip();
        }
    }

    private String pdfToText(Path path) throws Exception {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder out = new StringBuilder();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                out.append("## 第 ").append(page).append(" 页\n")
                        .append(stripper.getText(document).strip()).append("\n\n");
            }
            return out.toString().strip();
        }
    }

    private int pptxPageCount(Path path) {
        try (InputStream input = Files.newInputStream(path); XMLSlideShow ppt = new XMLSlideShow(input)) {
            return ppt.getSlides().size();
        } catch (Exception e) {
            throw new BusinessException(422, "读取 PowerPoint 页数失败: " + e.getMessage());
        }
    }

    private int pdfPageCount(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            throw new BusinessException(422, "读取 PDF 页数失败: " + e.getMessage());
        }
    }

    private ArtifactFormat formatOf(GeneratedArtifact artifact) {
        try {
            return ArtifactFormat.valueOf(artifact.getFormat());
        } catch (Exception e) {
            String path = artifact.getRelativePath().toLowerCase();
            if (path.endsWith(".pdf")) {
                return ArtifactFormat.PDF;
            }
            throw new BusinessException(422, "未知交付物格式: " + artifact.getFormat());
        }
    }

    private String safeHtml(String html) {
        String sanitized = html.replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?is)<(?:iframe|object|embed|link)[^>]*>.*?</(?:iframe|object|embed|link)>", "")
                .replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*')", "")
                .replaceAll("(?i)javascript:", "");
        String csp = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; "
                + "style-src 'unsafe-inline'; img-src data: blob:; font-src data:\">";
        int head = sanitized.toLowerCase().indexOf("<head>");
        return head >= 0 ? sanitized.substring(0, head + 6) + csp + sanitized.substring(head + 6)
                : csp + sanitized;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final class FilesRead {
        private FilesRead() {}

        private static String readString(Path path) {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new BusinessException(422, "读取 HTML 预览失败: " + e.getMessage());
            }
        }
    }
}
