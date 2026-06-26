package com.projvault.pkc.file;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * P3 文档解析服务（Apache Tika）。
 *
 * 只解析 ADDED / MODIFIED 文件；ARCHIVE 类文件跳过（P3 不解压）；
 * 解析成功后按约 800 汉字切块写入 pkc_doc_chunk，FileAsset.parseStatus 更新为 PARSED/FAILED。
 *
 * 切块策略：按 '\n' 行切，累积超过 CHUNK_CHAR_LIMIT 时落一块；
 * 中文项目文档行较短，此策略效果等同于段落切割，足够 RAG 使用。
 */
@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    /** 每块最大字符数（约 800 汉字 ≈ 1200 token） */
    private static final int CHUNK_CHAR_LIMIT = 1600;

    /** 内嵌图片：单张上限 5MB、单文件最多 40 张 */
    private static final long MAX_IMG_BYTES = 5L * 1024 * 1024;
    private static final int MAX_IMGS_PER_FILE = 40;

    /** 跳过解析的类别（存档文件 P3 不处理） */
    private static final Set<String> SKIP_CATEGORY = Set.of(
            FileCategoryResolver.ARCHIVE, FileCategoryResolver.IMAGE);

    private final FileAssetRepository fileAssetRepository;
    private final DocChunkRepository docChunkRepository;
    private final DocImageRepository docImageRepository;
    private final com.projvault.pkc.scan.ScanCancellation scanCancellation;
    private final Tika tika = new Tika();

    public DocumentParserService(FileAssetRepository fileAssetRepository,
                                 DocChunkRepository docChunkRepository,
                                 DocImageRepository docImageRepository,
                                 com.projvault.pkc.scan.ScanCancellation scanCancellation) {
        this.fileAssetRepository = fileAssetRepository;
        this.docChunkRepository = docChunkRepository;
        this.docImageRepository = docImageRepository;
        this.scanCancellation = scanCancellation;
    }

    /**
     * 解析变更文件列表（仅 ADDED / MODIFIED），返回解析结果。
     *
     * @param changes    P2 产出的变更记录
     * @param projectRoot 项目根目录（用于还原绝对路径）
     */
    @Transactional
    public List<ParseResult> parseChanged(List<FileChangeRecord> changes, Path projectRoot, Long scanId) {
        List<ParseResult> results = new ArrayList<>();
        for (FileChangeRecord change : changes) {
            if (scanCancellation.isCancelled(scanId)) {
                log.info("[parse] 收到取消，停止文档解析（已处理 {}）", results.size());
                break;
            }
            ChangeType ct = change.changeType();
            if (ct != ChangeType.ADDED && ct != ChangeType.MODIFIED) {
                continue;
            }
            FileAsset asset = change.asset();
            if (SKIP_CATEGORY.contains(asset.getCategory())) {
                log.debug("[parse] 跳过（{}）: {}", asset.getCategory(), asset.getRelPath());
                continue;
            }
            ParseResult r = parseOne(asset, projectRoot);
            results.add(r);
        }
        return results;
    }

    // ─── 单文件解析 ───────────────────────────────────────────────────────────

    private ParseResult parseOne(FileAsset asset, Path projectRoot) {
        Path abs = projectRoot.resolve(Paths.get(asset.getRelPath().replace('/', java.io.File.separatorChar)));
        // The fingerprint has already confirmed that the physical content changed.
        // Remove stale derived evidence before attempting to parse the replacement.
        docChunkRepository.deleteByFileId(asset.getId());
        docImageRepository.deleteByFileId(asset.getId());
        if (!Files.isReadable(abs)) {
            return markFailed(asset, "文件不可读: " + abs);
        }

        String text;
        List<DocChunk> chunks;
        try {
            String ext = asset.getExt() == null ? "" : asset.getExt().toLowerCase();
            if (ext.equals("pdf")) {
                chunks = extractPdfPages(asset.getId(), abs);
                text = chunks.stream().map(DocChunk::getContent).reduce("", (a, b) -> a + "\n" + b);
            } else if (ext.equals("xlsx") || ext.equals("xls")) {
                text = extractExcel(abs);
                chunks = splitAndSave(asset.getId(), text);
            } else if (ext.equals("docx") && java.nio.file.Files.size(abs) < 30L * 1024 * 1024) {
                text = extractDocx(abs);
                chunks = splitAndSave(asset.getId(), text);
            } else {
                text = cleanTikaNoise(extractText(abs));
                chunks = splitAndSave(asset.getId(), text);
            }
        } catch (Exception e) {
            log.warn("[parse] Tika 提取失败: {} — {}", asset.getRelPath(), e.getMessage());
            return markFailed(asset, truncate(e.getMessage(), 200));
        }

        if (text == null || text.isBlank()) {
            return markFailed(asset, "内容为空或无法提取文本");
        }

        // 内嵌图片入库（docx/xlsx/pptx）
        List<DocImage> imgs = extractEmbeddedImages(abs, asset.getProjectId(), asset.getId());
        if (!imgs.isEmpty()) {
            docImageRepository.saveAll(imgs);
        }

        asset.setParseStatus("PARSED");
        asset.setUpdatedAt(LocalDateTime.now());
        fileAssetRepository.save(asset);

        log.info("[parse] {} → {} 块，内嵌图 {} 张", asset.getRelPath(), chunks.size(), imgs.size());
        return ParseResult.ok(asset.getId(), text, chunks);
    }

    // ─── Tika 文本提取 ───────────────────────────────────────────────────────

    private String extractText(Path path) throws Exception {
        // BodyContentHandler(-1) = 不限制输出长度
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        AutoDetectParser parser = new AutoDetectParser();
        try (InputStream stream = Files.newInputStream(path)) {
            parser.parse(stream, handler, metadata, context);
        }
        return handler.toString();
    }

    // ─── Excel 结构化提取（POI，按 sheet 输出表格行）──────────────────────────

    /** 单个表最多解析行数，避免超大表撑爆内存 */
    private static final int EXCEL_MAX_ROWS = 3000;

    private String extractExcel(Path path) throws Exception {
        StringBuilder sb = new StringBuilder();
        DataFormatter fmt = new DataFormatter();
        try (InputStream in = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(in)) {
            int sheetCount = wb.getNumberOfSheets();
            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) {
                    continue;
                }
                sb.append("## 工作表：").append(sheet.getSheetName()).append('\n');
                int rowCount = 0;
                for (Row row : sheet) {
                    if (++rowCount > EXCEL_MAX_ROWS) {
                        sb.append("…（行数超过 ").append(EXCEL_MAX_ROWS).append(" 已截断）\n");
                        break;
                    }
                    short last = row.getLastCellNum();
                    if (last <= 0) {
                        continue;
                    }
                    StringBuilder line = new StringBuilder();
                    boolean any = false;
                    for (int c = 0; c < last; c++) {
                        Cell cell = row.getCell(c);
                        String v = cell == null ? "" : fmt.formatCellValue(cell).strip();
                        if (!v.isEmpty()) {
                            any = true;
                        }
                        line.append(v);
                        if (c < last - 1) {
                            line.append(" | ");
                        }
                    }
                    if (any) {
                        int firstRow = row.getRowNum() + 1;
                        String range = sheet.getSheetName() + "!A" + firstRow + ":"
                                + excelColumn(last) + firstRow;
                        sb.append('[').append(range).append("] ").append(line).append('\n');
                    }
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    // ─── docx 结构化提取（POI，仅正文，无元数据噪声）───────────────────────

    private String extractDocx(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path);
             org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(in)) {
            StringBuilder out = new StringBuilder();
            int tableIndex = 0;
            for (org.apache.poi.xwpf.usermodel.IBodyElement element : doc.getBodyElements()) {
                if (element instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph) {
                    String value = paragraph.getText();
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    String style = paragraph.getStyle() == null ? "" : paragraph.getStyle().toLowerCase();
                    if (style.contains("heading1")) {
                        out.append("# ");
                    } else if (style.contains("heading2")) {
                        out.append("## ");
                    } else if (style.contains("heading3")) {
                        out.append("### ");
                    }
                    out.append(value.strip()).append('\n');
                } else if (element instanceof org.apache.poi.xwpf.usermodel.XWPFTable table) {
                    tableIndex++;
                    out.append("## 表格 ").append(tableIndex).append('\n');
                    int rowIndex = 0;
                    for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                        rowIndex++;
                        List<org.apache.poi.xwpf.usermodel.XWPFTableCell> cells = row.getTableCells();
                        out.append("[表格").append(tableIndex).append("!A").append(rowIndex)
                                .append(':').append(excelColumn((short) cells.size())).append(rowIndex).append("] ");
                        for (int i = 0; i < cells.size(); i++) {
                            if (i > 0) {
                                out.append(" | ");
                            }
                            out.append(cells.get(i).getText().strip());
                        }
                        out.append('\n');
                    }
                }
            }
            return out.toString();
        }
    }

    private List<DocChunk> extractPdfPages(Long fileId, Path path) throws Exception {
        List<DocChunk> chunks = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int seq = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).strip();
                if (text.isBlank()) {
                    continue;
                }
                for (String part : splitText(text, CHUNK_CHAR_LIMIT)) {
                    chunks.add(saveChunk(fileId, seq++, "第 " + page + " 页", page, part));
                }
            }
        }
        return chunks;
    }

    // ─── 清洗 docx/OOXML 所需噪声（Tika 把包部件附在正文后）───

    private static String cleanTikaNoise(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        // 截断 OOXML 元数据尾部（Tika 把包部件内容附在正文之后）
        int cut = -1;
        for (String mk : new String[]{"[Content_Types].xml", "docProps/app.xml",
                "docProps/core.xml", "docProps/custom.xml", "word/document.xml",
                "word/styles.xml", "_rels/.rels"}) {
            int idx = text.indexOf(mk);
            if (idx > 50 && (cut < 0 || idx < cut)) {
                cut = idx;
            }
        }
        if (cut > 0) {
            text = text.substring(0, cut);
        }
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String t = line.strip();
            if (t.isEmpty()) {
                sb.append('\n');
                continue;
            }
            String low = t.toLowerCase();
            // OOXML 包部件路径行（docProps/app.xml、word/styles.xml 等）
            if (low.endsWith(".xml")
                    && (low.startsWith("docprops/") || low.startsWith("word/")
                    || low.startsWith("customxml/") || low.startsWith("xl/")
                    || low.startsWith("ppt/") || low.startsWith("_rels/")
                    || low.equals("[content_types].xml"))) {
                continue;
            }
            // 文档内嵌媒体引用（word/media/image1.png 等）
            if (low.startsWith("word/media/") || low.startsWith("xl/media/")
                    || low.startsWith("ppt/media/")) {
                continue;
            }
            // 纯 base64 长串
            if (t.length() > 80 && t.chars().allMatch(c ->
                    (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=')) {
                continue;
            }
            // 域代码 / 模板 / Office 版本噪声
            if (t.contains("MERGEFORMAT") || t.startsWith("Normal.dotm") || t.contains("WPS Office_")) {
                continue;
            }
            String collapsed = line;
            while (collapsed.contains("  ")) {
                collapsed = collapsed.replace("  ", " ");
            }
            sb.append(collapsed).append('\n');
        }
        return sb.toString();
    }

    // ─── 分块 ────────────────────────────────────────────────────────────────

    private List<DocChunk> splitAndSave(Long fileId, String text) {
        List<DocChunk> saved = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder buf = new StringBuilder();
        int seq = 0;
        String heading = null;
        String bufferHeading = null;

        for (String line : lines) {
            if (line.strip().startsWith("#")) {
                heading = line.replaceFirst("^#+\\s*", "").strip();
            }
            if (buf.isEmpty()) {
                bufferHeading = heading;
            }
            buf.append(line).append('\n');
            if (buf.length() >= CHUNK_CHAR_LIMIT) {
                saved.add(saveChunk(fileId, seq++, bufferHeading, 0, buf.toString()));
                buf.setLength(0);
            }
        }
        if (!buf.isEmpty()) {
            saved.add(saveChunk(fileId, seq, bufferHeading, 0, buf.toString()));
        }
        return saved;
    }

    private DocChunk saveChunk(Long fileId, int seq, String content) {
        return saveChunk(fileId, seq, null, 0, content);
    }

    private DocChunk saveChunk(Long fileId, int seq, String headingPath, int pageNo, String content) {
        DocChunk chunk = new DocChunk();
        chunk.setFileId(fileId);
        chunk.setSeq(seq);
        chunk.setHeadingPath(headingPath);
        chunk.setPageNo(pageNo);
        chunk.setContent(content.strip());
        chunk.setTokenCount((int) (content.length() / 1.5));
        return docChunkRepository.save(chunk);
    }

    private List<String> splitText(String text, int maxChars) {
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < text.length(); start += maxChars) {
            parts.add(text.substring(start, Math.min(start + maxChars, text.length())));
        }
        return parts;
    }

    private String excelColumn(short cellCount) {
        int value = Math.max(1, cellCount);
        StringBuilder name = new StringBuilder();
        while (value > 0) {
            value--;
            name.append((char) ('A' + value % 26));
            value /= 26;
        }
        return name.reverse().toString();
    }

    // ─── 工具 ────────────────────────────────────────────────────────────────

    /** 从 OOXML（docx/xlsx/pptx）的 ZIP 包中提取 media 图片。.doc/.xls 二进制格式跳过。 */
    private List<DocImage> extractEmbeddedImages(Path path, Long projectId, Long fileId) {
        List<DocImage> out = new ArrayList<>();
        String fn = path.getFileName().toString().toLowerCase();
        if (!(fn.endsWith(".docx") || fn.endsWith(".xlsx") || fn.endsWith(".pptx"))) {
            return out;
        }
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            int seq = 0;
            while (en.hasMoreElements() && out.size() < MAX_IMGS_PER_FILE) {
                ZipEntry e = en.nextElement();
                String n = e.getName().toLowerCase();
                boolean inMedia = n.startsWith("word/media/") || n.startsWith("xl/media/")
                        || n.startsWith("ppt/media/");
                if (!inMedia || !isImageName(n) || e.getSize() > MAX_IMG_BYTES) {
                    continue;
                }
                byte[] bytes;
                try (InputStream in = zip.getInputStream(e)) {
                    bytes = in.readAllBytes();
                }
                if (bytes.length == 0 || bytes.length > MAX_IMG_BYTES) {
                    continue;
                }
                DocImage di = new DocImage();
                di.setProjectId(projectId);
                di.setFileId(fileId);
                di.setSeq(seq++);
                di.setMediaName(e.getName().substring(e.getName().lastIndexOf('/') + 1));
                di.setExt(extOf(n));
                di.setSize(bytes.length);
                di.setData(bytes);
                out.add(di);
            }
        } catch (Exception ex) {
            log.warn("[parse] 内嵌图片提取失败: {} — {}", path.getFileName(), ex.getMessage());
        }
        return out;
    }

    private static boolean isImageName(String n) {
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp")
                || n.endsWith(".svg");
    }

    private static String extOf(String n) {
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1) : "";
    }

    private ParseResult markFailed(FileAsset asset, String reason) {
        asset.setParseStatus("FAILED");
        asset.setUpdatedAt(LocalDateTime.now());
        fileAssetRepository.save(asset);
        return ParseResult.fail(asset.getId(), reason);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "未知错误";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
