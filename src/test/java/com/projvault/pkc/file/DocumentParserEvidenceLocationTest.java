package com.projvault.pkc.file;

import com.projvault.pkc.scan.ScanCancellation;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentParserEvidenceLocationTest {

    @TempDir
    Path tempDir;

    @Test
    void preservesPdfPageNumbersInChunks() throws Exception {
        Path pdf = tempDir.resolve("evidence.pdf");
        try (PDDocument document = new PDDocument()) {
            addPdfPage(document, "First page evidence");
            addPdfPage(document, "Second page evidence");
            document.save(pdf.toFile());
        }
        FileAsset asset = asset(1L, "evidence.pdf", "pdf");

        ParseResult result = parser().parseChanged(
                List.of(FileChangeRecord.added(asset)), tempDir, 1L).get(0);

        assertThat(result.parseOk()).isTrue();
        assertThat(result.chunks()).extracting(DocChunk::getPageNo).containsExactly(1, 2);
        assertThat(result.chunks()).extracting(DocChunk::getHeadingPath)
                .containsExactly("第 1 页", "第 2 页");
    }

    @Test
    void preservesExcelSheetAndCellRange() throws Exception {
        Path xlsx = tempDir.resolve("evidence.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Data");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("Name");
            row.createCell(1).setCellValue("Value");
            try (var output = Files.newOutputStream(xlsx)) {
                workbook.write(output);
            }
        }
        FileAsset asset = asset(2L, "evidence.xlsx", "xlsx");

        ParseResult result = parser().parseChanged(
                List.of(FileChangeRecord.added(asset)), tempDir, 2L).get(0);

        assertThat(result.parseOk()).isTrue();
        assertThat(result.chunks().get(0).getHeadingPath()).isEqualTo("工作表：Data");
        assertThat(result.chunks().get(0).getContent()).contains("[Data!A1:B1]", "Name | Value");
    }

    private DocumentParserService parser() {
        FileAssetRepository files = mock(FileAssetRepository.class);
        DocChunkRepository chunks = mock(DocChunkRepository.class);
        DocImageRepository images = mock(DocImageRepository.class);
        AtomicLong ids = new AtomicLong(1);
        when(files.save(any(FileAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunks.save(any(DocChunk.class))).thenAnswer(invocation -> {
            DocChunk chunk = invocation.getArgument(0);
            chunk.setId(ids.getAndIncrement());
            return chunk;
        });
        return new DocumentParserService(files, chunks, images, new ScanCancellation());
    }

    private FileAsset asset(Long id, String path, String ext) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setProjectId(1L);
        asset.setName(path);
        asset.setRelPath(path);
        asset.setExt(ext);
        asset.setCategory("DOCUMENT");
        return asset;
    }

    private void addPdfPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 720);
            content.showText(text);
            content.endText();
        }
    }
}
