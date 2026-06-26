package com.projvault.pkc.artifact;

import com.projvault.common.BusinessException;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class ArtifactFileValidator {

    public List<String> validate(Path path, ArtifactFormat format) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) == 0) {
                throw new BusinessException(422, "交付物文件为空或不存在");
            }
            List<String> checks = new ArrayList<>();
            checks.add("文件已成功落盘（" + Files.size(path) + " 字节）");
            switch (format) {
                case MARKDOWN -> validateText(path, checks, "Markdown 正文非空");
                case SQL -> validateText(path, checks, "SQL 正文完整");
                case HTML -> validateHtml(path, checks);
                case DOCX -> validateDocx(path, checks);
                case PPTX -> validatePptx(path, checks);
                case PDF -> validatePdf(path, checks);
            }
            return List.copyOf(checks);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(422, "交付物格式校验失败: " + e.getMessage());
        }
    }

    private void validateText(Path path, List<String> checks, String message) throws Exception {
        if (Files.readString(path, StandardCharsets.UTF_8).isBlank()) {
            throw new BusinessException(422, "交付物正文为空");
        }
        checks.add(message);
    }

    private void validateHtml(Path path, List<String> checks) throws Exception {
        String html = Files.readString(path, StandardCharsets.UTF_8).toLowerCase();
        if (!html.contains("<html") || !html.contains("</html>")) {
            throw new BusinessException(422, "HTML 文档结构未闭合");
        }
        checks.add("HTML 文档结构闭合");
    }

    private void validateDocx(Path path, List<String> checks) throws Exception {
        try (InputStream input = Files.newInputStream(path); XWPFDocument doc = new XWPFDocument(input)) {
            int blocks = doc.getParagraphs().size() + doc.getTables().size();
            if (blocks == 0) {
                throw new BusinessException(422, "Word 文档没有可预览内容");
            }
            checks.add("Word 可读取，共 " + blocks + " 个正文块");
        }
    }

    private void validatePptx(Path path, List<String> checks) throws Exception {
        try (InputStream input = Files.newInputStream(path); XMLSlideShow ppt = new XMLSlideShow(input)) {
            if (ppt.getSlides().isEmpty()) {
                throw new BusinessException(422, "PowerPoint 没有幻灯片");
            }
            checks.add("PowerPoint 可读取，共 " + ppt.getSlides().size() + " 页");
        }
    }

    private void validatePdf(Path path, List<String> checks) throws Exception {
        byte[] signature;
        try (InputStream input = Files.newInputStream(path)) {
            signature = input.readNBytes(5);
        }
        if (signature.length < 5 || signature[0] != '%' || signature[1] != 'P'
                || signature[2] != 'D' || signature[3] != 'F') {
            throw new BusinessException(422, "PDF 文件头无效");
        }
        checks.add("PDF 文件头有效");
    }
}
