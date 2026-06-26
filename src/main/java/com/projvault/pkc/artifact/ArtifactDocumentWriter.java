package com.projvault.pkc.artifact;

import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ArtifactDocumentWriter {

    @Value("${projvault.pdf.font-path:}")
    private String configuredFontPath = "";

    public void write(Path target,
                      ArtifactFormat format,
                      String title,
                      String content,
                      List<String> sources) throws Exception {
        switch (format) {
            case MARKDOWN -> Files.writeString(target, appendMarkdownSources(content, sources), StandardCharsets.UTF_8);
            case HTML -> Files.writeString(target, renderHtml(title, content, sources), StandardCharsets.UTF_8);
            case SQL -> Files.writeString(target, renderSql(content, sources), StandardCharsets.UTF_8);
            case DOCX -> writeDocx(target, title, content, sources);
            case PPTX -> writePptx(target, title, content, sources);
            case PDF -> writePdf(target, title, content, sources);
        }
    }

    private void writePdf(Path target, String title, String content, List<String> sources) throws Exception {
        try (PDDocument document = new PDDocument()) {
            try (PdfFontHandle handle = loadPdfFont(document)) {
                PDType0Font font = handle.font();
                List<PdfLine> lines = new ArrayList<>();
                lines.addAll(wrapPdfLine(font, title, 18, 495));
                lines.add(new PdfLine("", 8));
                for (String raw : content.split("\\R")) {
                    String text = raw.strip();
                    float size = text.startsWith("# ") ? 18
                            : text.startsWith("## ") ? 15
                            : text.startsWith("### ") ? 13 : 11;
                    text = text.replaceFirst("^#{1,6}\\s+", "")
                            .replaceAll("[*_`]", "")
                            .replaceFirst("^>\\s*", "")
                            .replaceFirst("^[-+]\\s+", "• ");
                    lines.addAll(wrapPdfLine(font, text, size, 495));
                    if (text.isBlank()) {
                        lines.add(new PdfLine("", 6));
                    }
                }
                lines.add(new PdfLine("", 8));
                lines.addAll(wrapPdfLine(font, "资料来源", 15, 495));
                for (String source : sources) {
                    lines.addAll(wrapPdfLine(font, "• " + source, 10, 495));
                }
                renderPdfLines(document, font, lines);
                document.save(target.toFile());
            }
        }
    }

    private void renderPdfLines(PDDocument document, PDType0Font font, List<PdfLine> lines) throws Exception {
        PDPage page = null;
        PDPageContentStream stream = null;
        float y = 0;
        try {
            for (PdfLine line : lines) {
                float leading = Math.max(14, line.fontSize() * 1.55f);
                if (page == null || y - leading < 52) {
                    if (stream != null) {
                        stream.close();
                    }
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - 52;
                }
                if (!line.text().isBlank()) {
                    stream.beginText();
                    stream.setFont(font, line.fontSize());
                    stream.newLineAtOffset(50, y);
                    stream.showText(line.text());
                    stream.endText();
                }
                y -= leading;
            }
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    private List<PdfLine> wrapPdfLine(PDType0Font font, String raw, float fontSize, float maxWidth) {
        String text = pdfSafeText(font, raw);
        if (text.isBlank()) {
            return List.of(new PdfLine("", fontSize));
        }
        List<PdfLine> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            String candidate = current + character;
            try {
                if (!current.isEmpty() && font.getStringWidth(candidate) / 1000f * fontSize > maxWidth) {
                    lines.add(new PdfLine(current.toString(), fontSize));
                    current.setLength(0);
                }
            } catch (Exception ignored) {
                // pdfSafeText already removed unsupported glyphs.
            }
            current.append(character);
            offset += Character.charCount(codePoint);
        }
        if (!current.isEmpty()) {
            lines.add(new PdfLine(current.toString(), fontSize));
        }
        return lines;
    }

    private String pdfSafeText(PDType0Font font, String value) {
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            try {
                font.getStringWidth(character);
                out.append(character);
            } catch (Exception ignored) {
                out.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        return out.toString();
    }

    private PdfFontHandle loadPdfFont(PDDocument document) throws Exception {
        List<Path> candidates = pdfFontCandidates();
        List<String> failures = new ArrayList<>();
        for (Path candidate : candidates) {
            try {
                String lower = candidate.getFileName().toString().toLowerCase();
                if (lower.endsWith(".ttc") || lower.endsWith(".otc")) {
                    TrueTypeCollection collection = new TrueTypeCollection(candidate.toFile());
                    AtomicReference<TrueTypeFont> selected = new AtomicReference<>();
                    collection.processAllFonts(font -> { if (selected.get() == null) selected.set(font); });
                    if (selected.get() == null) {
                        collection.close();
                        continue;
                    }
                    return new PdfFontHandle(PDType0Font.load(document, selected.get(), true), collection);
                }
                return new PdfFontHandle(PDType0Font.load(document, candidate.toFile()), null);
            } catch (Exception e) {
                failures.add(candidate + ": " + e.getMessage());
            }
        }
        throw new IllegalStateException("未找到可嵌入 PDF 的中文字体。请设置 PROJVAULT_PDF_FONT_PATH"
                + (failures.isEmpty() ? "" : "；尝试失败: " + String.join(" | ", failures)));
    }

    List<Path> pdfFontCandidates() {
        List<String> known = List.of(
                configuredFontPath,
                System.getProperty("projvault.pdf.font-path", ""),
                System.getenv().getOrDefault("PROJVAULT_PDF_FONT_PATH", ""),
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsunb.ttf",
                "/System/Library/Fonts/PingFang.ttc",
                "/System/Library/Fonts/STHeiti Light.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        Set<Path> result = new LinkedHashSet<>();
        known.stream().filter(path -> path != null && !path.isBlank()).map(Path::of)
                .filter(Files::isRegularFile).forEach(result::add);
        return List.copyOf(result);
    }

    private record PdfLine(String text, float fontSize) {}

    private record PdfFontHandle(PDType0Font font, AutoCloseable resource) implements AutoCloseable {
        @Override public void close() throws Exception { if (resource != null) resource.close(); }
    }

    private String appendMarkdownSources(String content, List<String> sources) {
        return content.strip() + "\n\n## 资料来源\n"
                + sources.stream().map(source -> "- " + source).reduce("", (a, b) -> a + b + "\n");
    }

    private String renderSql(String content, List<String> sources) {
        StringBuilder out = new StringBuilder("-- AI 生成草稿，须经项目经理与数据库负责人审查\n");
        for (String source : sources) {
            out.append("-- 资料来源: ").append(source).append('\n');
        }
        out.append('\n').append(stripCodeFence(content)).append('\n');
        return out.toString();
    }

    private String renderHtml(String title, String content, List<String> sources) {
        StringBuilder body = new StringBuilder();
        boolean inList = false;
        for (String raw : content.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                if (inList) {
                    body.append("</ul>");
                    inList = false;
                }
                continue;
            }
            if (line.startsWith("### ")) {
                body.append("<h3>").append(escapeHtml(line.substring(4))).append("</h3>");
            } else if (line.startsWith("## ")) {
                body.append("<h2>").append(escapeHtml(line.substring(3))).append("</h2>");
            } else if (line.startsWith("# ")) {
                body.append("<h1>").append(escapeHtml(line.substring(2))).append("</h1>");
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList) {
                    body.append("<ul>");
                    inList = true;
                }
                body.append("<li>").append(escapeHtml(line.substring(2))).append("</li>");
            } else {
                if (inList) {
                    body.append("</ul>");
                    inList = false;
                }
                body.append("<p>").append(escapeHtml(line)).append("</p>");
            }
        }
        if (inList) {
            body.append("</ul>");
        }
        body.append("<h2>资料来源</h2><ul>");
        for (String source : sources) {
            body.append("<li>").append(escapeHtml(source)).append("</li>");
        }
        body.append("</ul>");
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><title>"
                + escapeHtml(title)
                + "</title><style>body{max-width:960px;margin:40px auto;padding:0 24px;font:16px/1.75 'Microsoft YaHei',sans-serif;color:#172125}h1,h2,h3{line-height:1.3}h2{margin-top:32px;border-bottom:1px solid #ccd5d1;padding-bottom:8px}li{margin:5px 0}</style></head><body>"
                + body + "</body></html>";
    }

    private void writeDocx(Path target, String title, String content, List<String> sources) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); OutputStream out = Files.newOutputStream(target)) {
            XWPFParagraph titleParagraph = doc.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontFamily("Microsoft YaHei");
            titleRun.setFontSize(22);

            boolean code = false;
            for (String raw : content.split("\\R")) {
                String line = raw.stripTrailing();
                if (line.strip().startsWith("```")) {
                    code = !code;
                    continue;
                }
                XWPFParagraph paragraph = doc.createParagraph();
                String text = line.strip();
                if (text.startsWith("### ")) {
                    paragraph.setStyle("Heading3");
                    text = text.substring(4);
                } else if (text.startsWith("## ")) {
                    paragraph.setStyle("Heading2");
                    text = text.substring(3);
                } else if (text.startsWith("# ")) {
                    paragraph.setStyle("Heading1");
                    text = text.substring(2);
                } else if (text.startsWith("- ") || text.startsWith("* ")) {
                    text = "• " + text.substring(2);
                }
                XWPFRun run = paragraph.createRun();
                run.setText(text);
                run.setFontFamily(code ? "Consolas" : "Microsoft YaHei");
                run.setFontSize(code ? 9 : 11);
            }

            XWPFParagraph sourceHeading = doc.createParagraph();
            sourceHeading.setStyle("Heading2");
            sourceHeading.createRun().setText("资料来源");
            for (String source : sources) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun run = p.createRun();
                run.setText("• " + source);
                run.setFontFamily("Microsoft YaHei");
            }
            doc.write(out);
        }
    }

    private void writePptx(Path target, String title, String content, List<String> sources) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(); OutputStream out = Files.newOutputStream(target)) {
            ppt.setPageSize(new Dimension(960, 540));
            addTitleSlide(ppt, title, "ProjVault 项目资料生成 · 待审查");
            List<SlideSection> sections = parseSections(content);
            for (SlideSection section : sections) {
                List<String> lines = section.lines().isEmpty() ? List.of("待补充") : section.lines();
                for (int offset = 0; offset < lines.size(); offset += 6) {
                    addContentSlide(ppt, section.title(), lines.subList(offset, Math.min(offset + 6, lines.size())));
                }
            }
            addContentSlide(ppt, "资料来源", sources);
            ppt.write(out);
        }
    }

    private void addTitleSlide(XMLSlideShow ppt, String title, String subtitle) {
        XSLFSlide slide = ppt.createSlide();
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle2D.Double(90, 155, 780, 120));
        addText(titleBox, title, 30, true, new Color(11, 122, 117));
        XSLFTextBox subBox = slide.createTextBox();
        subBox.setAnchor(new Rectangle2D.Double(120, 300, 720, 60));
        addText(subBox, subtitle, 16, false, new Color(83, 99, 105));
    }

    private void addContentSlide(XMLSlideShow ppt, String title, List<String> lines) {
        XSLFSlide slide = ppt.createSlide();
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle2D.Double(55, 32, 850, 62));
        addText(titleBox, title, 25, true, new Color(11, 122, 117));
        XSLFTextBox body = slide.createTextBox();
        body.setAnchor(new Rectangle2D.Double(70, 112, 820, 365));
        for (String line : lines) {
            XSLFTextParagraph paragraph = body.addNewTextParagraph();
            paragraph.setSpaceAfter(10d);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText("• " + cleanMarkdown(line));
            run.setFontFamily("Microsoft YaHei");
            run.setFontSize(17d);
            run.setFontColor(new Color(28, 41, 46));
        }
    }

    private void addText(XSLFTextBox box, String text, double size, boolean bold, Color color) {
        XSLFTextParagraph paragraph = box.addNewTextParagraph();
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(text);
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontColor(color);
    }

    private List<SlideSection> parseSections(String content) {
        List<SlideSection> sections = new ArrayList<>();
        String currentTitle = "项目要点";
        List<String> currentLines = new ArrayList<>();
        for (String raw : content.split("\\R")) {
            String line = raw.strip();
            if (line.startsWith("#")) {
                if (!currentLines.isEmpty()) {
                    sections.add(new SlideSection(currentTitle, List.copyOf(currentLines)));
                    currentLines.clear();
                }
                currentTitle = line.replaceFirst("^#+\\s*", "");
            } else if (!line.isBlank() && !line.startsWith("```")) {
                currentLines.add(line);
            }
        }
        if (!currentLines.isEmpty()) {
            sections.add(new SlideSection(currentTitle, List.copyOf(currentLines)));
        }
        return sections.isEmpty() ? List.of(new SlideSection("项目要点", List.of(content))) : sections;
    }

    private String stripCodeFence(String content) {
        return content.replaceFirst("(?s)^\\s*```(?:sql)?\\s*", "")
                .replaceFirst("(?s)\\s*```\\s*$", "")
                .strip();
    }

    private String cleanMarkdown(String value) {
        return value.replaceFirst("^[-*]\\s+", "")
                .replace("**", "")
                .replace("`", "")
                .strip();
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record SlideSection(String title, List<String> lines) {}
}
