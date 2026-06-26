package com.projvault.pkc.file;

import com.projvault.common.ApiResponse;
import com.projvault.pkc.project.Project;
import com.projvault.pkc.project.ProjectRepository;
import com.projvault.security.RequirePerm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件资产 REST API。
 *
 * GET /api/pkc/projects/{projectId}/files          分页列表（可按 docType / parseStatus 过滤）
 * GET /api/pkc/files/{fileId}                      单文件详情
 * GET /api/pkc/files/{fileId}/chunks               文档分块列表（RAG 预览）
 * GET /api/pkc/files/{fileId}/raw                  原始文件字节（图片预览）
 * GET /api/pkc/projects/{projectId}/file-stats     各 docType 数量统计
 */
@RestController
@RequestMapping("/api/pkc")
public class FileController {

    private final FileAssetRepository fileAssetRepository;
    private final DocChunkRepository docChunkRepository;
    private final ProjectRepository projectRepository;
    private final DocImageRepository docImageRepository;

    public FileController(FileAssetRepository fileAssetRepository,
                          DocChunkRepository docChunkRepository,
                          ProjectRepository projectRepository,
                          DocImageRepository docImageRepository) {
        this.fileAssetRepository = fileAssetRepository;
        this.docChunkRepository = docChunkRepository;
        this.projectRepository = projectRepository;
        this.docImageRepository = docImageRepository;
    }

    // ── 文件列表 ──────────────────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/files")
    @RequirePerm("pkc:file:view")
    public ApiResponse<Page<FileAssetDTO>> listFiles(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String parseStatus) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.ASC, "relPath"));

        Page<FileAsset> result;
        if (docType != null && !docType.isBlank()) {
            result = fileAssetRepository.findByProjectIdAndDocTypeAndDeletedFlagFalse(
                    projectId, docType, pageable);
        } else if (parseStatus != null && !parseStatus.isBlank()) {
            result = fileAssetRepository.findByProjectIdAndParseStatusAndDeletedFlagFalse(
                    projectId, parseStatus, pageable);
        } else {
            result = fileAssetRepository.findByProjectIdAndDeletedFlagFalse(projectId, pageable);
        }
        return ApiResponse.ok(result.map(a -> FileAssetDTO.from(a, duplicatePaths(a))));
    }

    // ── 单文件详情 ────────────────────────────────────────────────────────────

    @GetMapping("/files/{fileId}")
    @RequirePerm("pkc:file:view")
    public ApiResponse<FileAssetDTO> getFile(@PathVariable Long fileId) {
        return fileAssetRepository.findById(fileId)
                .map(a -> FileAssetDTO.from(a, duplicatePaths(a)))
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error(404, "文件不存在"));
    }

    // ── 分块列表（RAG 预览）──────────────────────────────────────────────────

    @GetMapping("/files/{fileId}/chunks")
    @RequirePerm("pkc:file:view")
    public ApiResponse<List<DocChunk>> listChunks(@PathVariable Long fileId) {
        return ApiResponse.ok(docChunkRepository.findByFileIdOrderBySeq(fileId));
    }

    // ── 文档内嵌图片 ──────────────────────────────────────────────────────────

    @GetMapping("/files/{fileId}/images")
    @RequirePerm("pkc:file:view")
    public ApiResponse<List<Map<String, Object>>> images(@PathVariable Long fileId) {
        List<Map<String, Object>> out = docImageRepository.findByFileIdOrderBySeq(fileId).stream()
                .map(im -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", im.getId());
                    m.put("mediaName", im.getMediaName());
                    m.put("ext", im.getExt());
                    m.put("size", im.getSize());
                    return m;
                })
                .collect(Collectors.toList());
        return ApiResponse.ok(out);
    }

    @GetMapping("/doc-images/{imageId}/raw")
    @RequirePerm("pkc:file:view")
    public ResponseEntity<byte[]> docImageRaw(@PathVariable Long imageId) {
        DocImage im = docImageRepository.findById(imageId).orElse(null);
        if (im == null || im.getData() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(imageMediaType(im.getExt())).body(im.getData());
    }

    // ── 单个分块（引用溯源：查看 AI 回答引用的原文段落）────────────────────────

    @GetMapping("/chunks/{chunkId}")
    @RequirePerm("pkc:file:view")
    public ApiResponse<Map<String, Object>> getChunk(@PathVariable Long chunkId) {
        DocChunk c = docChunkRepository.findById(chunkId).orElse(null);
        if (c == null) {
            return ApiResponse.error(404, "分块不存在");
        }
        FileAsset fa = fileAssetRepository.findById(c.getFileId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("fileId", c.getFileId());
        m.put("fileName", fa != null ? fa.getName() : null);
        m.put("relPath", fa != null ? fa.getRelPath() : null);
        m.put("headingPath", c.getHeadingPath());
        m.put("pageNo", c.getPageNo());
        m.put("content", c.getContent());
        return ApiResponse.ok(m);
    }

    // ── 原始字节（图片预览，限项目根目录内，防目录穿越）────────────────────────

    @GetMapping("/files/{fileId}/raw")
    @RequirePerm("pkc:file:view")
    public ResponseEntity<byte[]> raw(@PathVariable Long fileId) {
        FileAsset f = fileAssetRepository.findById(fileId).orElse(null);
        if (f == null) {
            return ResponseEntity.notFound().build();
        }
        Project proj = projectRepository.findById(f.getProjectId()).orElse(null);
        if (proj == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path root = Paths.get(proj.getRootPath()).normalize();
            Path target = root.resolve(f.getRelPath()).normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = Files.readAllBytes(target);
            return ResponseEntity.ok().contentType(imageMediaType(f.getExt())).body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private static MediaType imageMediaType(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        return switch (e) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "svg" -> MediaType.valueOf("image/svg+xml");
            case "bmp" -> MediaType.valueOf("image/bmp");
            case "webp" -> MediaType.valueOf("image/webp");
            case "tif", "tiff" -> MediaType.valueOf("image/tiff");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    // ── docType 数量统计 ──────────────────────────────────────────────────────

    private List<String> duplicatePaths(FileAsset asset) {
        if (asset == null || asset.getSha256() == null || asset.getSha256().isBlank()) {
            return List.of();
        }
        return fileAssetRepository
                .findByProjectIdAndSha256AndDeletedFlagFalse(asset.getProjectId(), asset.getSha256())
                .stream()
                .map(FileAsset::getRelPath)
                .sorted()
                .toList();
    }

    @GetMapping("/projects/{projectId}/file-stats")
    @RequirePerm("pkc:file:view")
    public ApiResponse<Map<String, Long>> fileStats(@PathVariable Long projectId) {
        List<Object[]> rows = fileAssetRepository.countByDocType(projectId);
        Map<String, Long> stats = rows.stream().collect(
                Collectors.toMap(
                        r -> r[0] == null ? "未分类" : (String) r[0],
                        r -> (Long) r[1]
                ));
        return ApiResponse.ok(stats);
    }
}
