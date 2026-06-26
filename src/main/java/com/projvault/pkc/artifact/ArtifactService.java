package com.projvault.pkc.artifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projvault.ai.RagAnswer;
import com.projvault.common.BusinessException;
import com.projvault.pkc.file.DocChunk;
import com.projvault.pkc.file.FileAsset;
import com.projvault.pkc.file.FileAssetRepository;
import com.projvault.pkc.project.Project;
import com.projvault.pkc.project.ProjectRepository;
import com.projvault.pkc.rag.ChunkRetriever;
import com.projvault.pkc.rag.RagService;
import com.projvault.pkc.rag.ScoredChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ArtifactService {

    public static final String REVIEW_ROOT = "AI交付物/待审查";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ProjectRepository projectRepository;
    private final GeneratedArtifactRepository artifactRepository;
    private final ChunkRetriever chunkRetriever;
    private final FileAssetRepository fileAssetRepository;
    private final RagService ragService;
    private final ArtifactDocumentWriter documentWriter;
    private final ArtifactContentValidator contentValidator;
    private final ArtifactFileValidator fileValidator;
    private final ArtifactQualityService qualityService;
    private final ArtifactEvidenceScope evidenceScope;
    private final ArtifactFolderService folderService;

    public ArtifactService(ProjectRepository projectRepository,
                           GeneratedArtifactRepository artifactRepository,
                           ChunkRetriever chunkRetriever,
                           FileAssetRepository fileAssetRepository,
                           RagService ragService,
                           ArtifactDocumentWriter documentWriter,
                           ArtifactContentValidator contentValidator,
                           ArtifactFileValidator fileValidator,
                           ArtifactQualityService qualityService,
                           ArtifactEvidenceScope evidenceScope,
                           ArtifactFolderService folderService) {
        this.projectRepository = projectRepository;
        this.artifactRepository = artifactRepository;
        this.chunkRetriever = chunkRetriever;
        this.fileAssetRepository = fileAssetRepository;
        this.ragService = ragService;
        this.documentWriter = documentWriter;
        this.contentValidator = contentValidator;
        this.fileValidator = fileValidator;
        this.qualityService = qualityService;
        this.evidenceScope = evidenceScope;
        this.folderService = folderService;
    }

    @Transactional
    public GeneratedArtifactDTO generate(Long projectId, GenerateArtifactRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在: " + projectId));
        validateFormat(request.getArtifactType(), request.getFormat());

        String retrievalQuery = retrievalQuery(request);
        List<ScoredChunk> evidence = chunkRetriever.retrieve(
                projectId, retrievalQuery, Math.max(5, Math.min(request.getTopK(), 30)));
        evidence = evidenceScope.apply(request.getInstructions(), evidence,
                fileAssetRepository.findByProjectIdAndDeletedFlagFalse(projectId));
        if (evidence.isEmpty()) {
            throw new BusinessException(422, "没有检索到足够的项目证据，未生成文件");
        }

        Map<Long, FileAsset> fileMap = loadFileMap(evidence);
        List<String> sources = sourceFiles(evidence, fileMap);
        List<ArtifactEvidenceDTO> evidenceItems = evidenceItems(evidence, fileMap);
        List<String> contexts = contexts(evidence, fileMap);
        RagAnswer answer = ragService.answerFromContexts(buildInstruction(project, request), contexts);
        if (answer == null || !answer.grounded() || answer.answer() == null || answer.answer().isBlank()) {
            throw new BusinessException(503, "回答模型暂时不可用，未生成文件");
        }
        contentValidator.validate(request.getFormat(), answer.answer());
        return writeArtifact(project, null, request.getArtifactType(), request.getFormat(),
                request.getTitle(), request.getInstructions(), request.getOutputDir(), request.getFileName(),
                answer.answer(), sources, evidenceItems, 1, null);
    }

    @Transactional
    public GeneratedArtifactDTO createDerived(GeneratedArtifact parent,
                                              String title,
                                              String instructions,
                                              String content) {
        Project project = projectRepository.findById(parent.getProjectId())
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        ArtifactType type = ArtifactType.valueOf(parent.getArtifactType());
        ArtifactFormat format = ArtifactFormat.valueOf(parent.getFormat());
        contentValidator.validate(format, content);

        Long rootId = parent.getRootArtifactId() == null ? parent.getId() : parent.getRootArtifactId();
        GeneratedArtifact latest = artifactRepository
                .findTopByProjectIdAndRootArtifactIdOrderByVersionNoDesc(parent.getProjectId(), rootId);
        int version = Math.max(parent.getVersionNo(), latest == null ? 1 : latest.getVersionNo()) + 1;
        String parentDir = Path.of(parent.getRelativePath()).getParent() == null
                ? REVIEW_ROOT : Path.of(parent.getRelativePath()).getParent().toString().replace('\\', '/');
        String fileName = title.strip() + "-v" + version;
        return writeArtifact(project, parent, type, format, title, instructions, parentDir, fileName,
                content, sourceFilesOf(parent), evidenceOf(parent), version, rootId);
    }

    public List<GeneratedArtifactDTO> list(Long projectId) {
        return artifactRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<GeneratedArtifactDTO> trash(Long projectId) {
        return artifactRepository.findByProjectIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(projectId).stream()
                .map(this::toDto)
                .toList();
    }

    public GeneratedArtifactDTO get(Long artifactId) {
        return toDto(getEntity(artifactId));
    }

    @Transactional
    public GeneratedArtifactDTO move(Long artifactId, ArtifactMoveRequest request) {
        GeneratedArtifact artifact = getEntity(artifactId);
        requireActive(artifact);
        Project project = projectRepository.findById(artifact.getProjectId())
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        String folderPath = folderService.folderPath(artifact.getProjectId(), request.getFolderId());
        Path root = Path.of(project.getRootPath()).toAbsolutePath().normalize();
        Path source = resolveArtifactPath(artifact);
        Path directory = resolveOutputDir(root, folderPath);
        ArtifactFormat format = ArtifactFormat.valueOf(artifact.getFormat());
        String requestedName = request.getFileName() == null || request.getFileName().isBlank()
                ? request.getTitle() : request.getFileName();
        Path target = updateTarget(directory, requestedName, format, source);
        try {
            Files.createDirectories(directory);
            if (!source.equals(target)) {
                moveFile(source, target);
            }
            artifact.setTitle(request.getTitle().strip());
            artifact.setRelativePath(root.relativize(target).toString().replace('\\', '/'));
            artifact.setSha256(sha256(target));
            artifact.setFileSize(Files.size(target));
            return toDto(artifactRepository.save(artifact));
        } catch (Exception e) {
            throw new BusinessException(500, "移动或重命名交付物失败: " + safeMessage(e));
        }
    }

    @Transactional
    public GeneratedArtifactDTO delete(Long artifactId) {
        GeneratedArtifact artifact = getEntity(artifactId);
        requireActive(artifact);
        Project project = projectRepository.findById(artifact.getProjectId())
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        Path root = Path.of(project.getRootPath()).toAbsolutePath().normalize();
        Path source = resolveArtifactPath(artifact);
        Path recycleDir = root.resolve("AI交付物/回收站").normalize();
        Path target = recycleDir.resolve(artifact.getId() + "-" + source.getFileName()).normalize();
        if (Files.exists(target)) {
            target = recycleDir.resolve(artifact.getId() + "-" + FILE_TIME.format(LocalDateTime.now())
                    + "-" + source.getFileName()).normalize();
        }
        try {
            Files.createDirectories(recycleDir);
            moveFile(source, target);
            artifact.setOriginalRelativePath(artifact.getRelativePath());
            artifact.setRelativePath(root.relativize(target).toString().replace('\\', '/'));
            artifact.setDeletedAt(LocalDateTime.now());
            return toDto(artifactRepository.save(artifact));
        } catch (Exception e) {
            throw new BusinessException(500, "交付物移入回收站失败: " + safeMessage(e));
        }
    }

    @Transactional
    public GeneratedArtifactDTO restore(Long artifactId) {
        GeneratedArtifact artifact = getEntity(artifactId);
        if (artifact.getDeletedAt() == null || artifact.getOriginalRelativePath() == null) {
            throw new BusinessException(409, "该交付物不在回收站");
        }
        Project project = projectRepository.findById(artifact.getProjectId())
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        Path root = Path.of(project.getRootPath()).toAbsolutePath().normalize();
        Path source = resolveArtifactPath(artifact);
        Path requested = root.resolve(artifact.getOriginalRelativePath()).normalize();
        if (!requested.startsWith(root)) {
            throw new BusinessException("原始交付物路径无效");
        }
        Path target = requested;
        if (Files.exists(target)) {
            String fileName = requested.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String base = dot > 0 ? fileName.substring(0, dot) : fileName;
            String suffix = dot > 0 ? fileName.substring(dot) : "";
            target = requested.resolveSibling(base + "-恢复-" + FILE_TIME.format(LocalDateTime.now()) + suffix);
        }
        try {
            Files.createDirectories(target.getParent());
            moveFile(source, target);
            artifact.setRelativePath(root.relativize(target).toString().replace('\\', '/'));
            artifact.setDeletedAt(null);
            artifact.setOriginalRelativePath(null);
            return toDto(artifactRepository.save(artifact));
        } catch (Exception e) {
            throw new BusinessException(500, "恢复交付物失败: " + safeMessage(e));
        }
    }

    @Transactional
    public GeneratedArtifactDTO review(Long artifactId, ReviewArtifactRequest request) {
        GeneratedArtifact artifact = getEntity(artifactId);
        String status = request.getStatus().strip().toUpperCase();
        if (!Set.of("APPROVED", "REJECTED").contains(status)) {
            throw new BusinessException("审查状态仅支持 APPROVED 或 REJECTED");
        }
        if (status.equals("APPROVED")) {
            if (artifact.getPreviewedAt() == null) {
                throw new BusinessException(409, "请先在线预览交付物，再执行通过");
            }
            if (artifact.getQualityStatus() == null || artifact.getQualityStatus().equals("FAILED")) {
                throw new BusinessException(409, "质量检查未完成或未通过，不能批准");
            }
        }
        artifact.setReviewStatus(status);
        artifact.setReviewComment(blankToNull(request.getComment()));
        artifact.setReviewedAt(LocalDateTime.now());
        return toDto(artifactRepository.save(artifact));
    }

    public GeneratedArtifact getEntity(Long artifactId) {
        return artifactRepository.findById(artifactId)
                .orElseThrow(() -> new BusinessException(404, "交付物不存在: " + artifactId));
    }

    public Path resolveArtifactPath(GeneratedArtifact artifact) {
        Project project = projectRepository.findById(artifact.getProjectId())
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        Path root = Path.of(project.getRootPath()).toAbsolutePath().normalize();
        Path target = root.resolve(artifact.getRelativePath()).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new BusinessException(404, "交付物文件不存在或路径无效");
        }
        return target;
    }

    public List<Map<String, Object>> templates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        for (ArtifactType type : ArtifactType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", type.name());
            item.put("label", type.getLabel());
            item.put("guidance", type.getGuidance());
            item.put("formats", allowedFormats(type).stream().map(Enum::name).toList());
            templates.add(item);
        }
        return templates;
    }

    private String retrievalQuery(GenerateArtifactRequest request) {
        String focused = (request.getTitle() + " " + nullToEmpty(request.getInstructions())).strip();
        if (request.getInstructions() != null && !request.getInstructions().isBlank()) {
            return focused;
        }
        String keywords = switch (request.getArtifactType()) {
            case PROJECT_REPORT -> "项目范围 进度 成果 合同 变更 风险 里程碑 待办";
            case DESIGN_SPEC -> "需求 总体设计 模块 接口 数据 安全 部署 验收";
            case DATABASE_DESIGN -> "数据库 数据模型 表结构 字段 约束 索引 接口 配置";
            case PRESENTATION -> "项目目标 范围 进度 成果 风险 计划 决策 汇报";
            case CUSTOM_DOCUMENT -> "项目范围 需求 设计 进度 交付 风险";
        };
        return focused + " " + keywords;
    }

    private String buildInstruction(Project project, GenerateArtifactRequest request) {
        String formatInstruction = switch (request.getFormat()) {
            case SQL -> "只输出可审查的 SQL 正文，不要使用 Markdown 代码围栏；未知字段用 TODO 注释，不得编造生产密码。";
            case PPTX -> "使用 Markdown 一级或二级标题划分幻灯片，每页提供不超过 6 条简洁要点。";
            default -> "使用清晰的 Markdown 标题与列表组织正文，明确标注待确认项。";
        };
        return "为项目《" + project.getName() + "》编写《" + request.getTitle() + "》。\n"
                + request.getArtifactType().getGuidance() + "\n"
                + formatInstruction + "\n"
                + "只能使用提供的项目资料；资料不足时写‘待确认’，不得编造人名、金额、日期、地址、接口或配置。\n"
                + "用户补充要求：" + nullToEmpty(request.getInstructions());
    }

    private Map<Long, FileAsset> loadFileMap(List<ScoredChunk> evidence) {
        List<Long> ids = evidence.stream().map(item -> item.chunk().getFileId()).distinct().toList();
        Map<Long, FileAsset> files = new LinkedHashMap<>();
        for (FileAsset file : fileAssetRepository.findAllById(ids)) {
            files.put(file.getId(), file);
        }
        return files;
    }

    private List<String> sourceFiles(List<ScoredChunk> evidence, Map<Long, FileAsset> fileMap) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (ScoredChunk item : evidence) {
            FileAsset file = fileMap.get(item.chunk().getFileId());
            if (file != null) {
                sources.add(file.getRelPath());
            }
            if (sources.size() >= 12) {
                break;
            }
        }
        return List.copyOf(sources);
    }

    private List<String> contexts(List<ScoredChunk> evidence, Map<Long, FileAsset> fileMap) {
        List<String> contexts = new ArrayList<>();
        int totalChars = 0;
        for (ScoredChunk item : evidence) {
            DocChunk chunk = item.chunk();
            FileAsset file = fileMap.get(chunk.getFileId());
            if (file == null || chunk.getContent() == null) {
                continue;
            }
            String content = chunk.getContent();
            if (content.length() > 6000) {
                content = content.substring(0, 6000);
            }
            String context = "【来源：" + file.getRelPath() + "】\n" + content;
            if (totalChars + context.length() > 60_000) {
                break;
            }
            contexts.add(context);
            totalChars += context.length();
        }
        return contexts;
    }

    private Path resolveOutputDir(Path root, String requestedDir) {
        String raw = requestedDir == null || requestedDir.isBlank() ? REVIEW_ROOT : requestedDir.strip();
        Path relative = Path.of(raw.replace('\\', '/')).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new BusinessException("输出目录必须是项目根目录内的相对路径");
        }
        String normalized = relative.toString().replace('\\', '/');
        if (!normalized.equals(REVIEW_ROOT) && !normalized.startsWith(REVIEW_ROOT + "/")) {
            throw new BusinessException("AI 草稿必须放在 " + REVIEW_ROOT + " 或其子目录中");
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("输出目录越过项目根目录");
        }
        return target;
    }

    private Path uniqueTarget(Path dir, String requestedName, String title, ArtifactFormat format) {
        String base = requestedName == null || requestedName.isBlank() ? title : requestedName;
        String suffix = "." + format.getExtension();
        if (base.toLowerCase().endsWith(suffix)) {
            base = base.substring(0, base.length() - suffix.length());
        }
        base = sanitizeFileName(base);
        if (base.isBlank()) {
            base = "AI交付物-" + FILE_TIME.format(LocalDateTime.now());
        }
        Path target = dir.resolve(base + suffix);
        if (!Files.exists(target)) {
            return target;
        }
        return dir.resolve(base + "-" + FILE_TIME.format(LocalDateTime.now()) + suffix);
    }

    private String sanitizeFileName(String value) {
        String invalidWindowsChars = "[<>:\\\"/\\\\|?*\\p{Cntrl}]";
        return value.strip()
                .replaceAll(invalidWindowsChars, "-")
                .replaceAll("[. ]+$", "")
                .replaceAll("\\s+", " ");
    }

    private void validateFormat(ArtifactType type, ArtifactFormat format) {
        if (!allowedFormats(type).contains(format)) {
            throw new BusinessException(type.getLabel() + "不支持 " + format + " 格式");
        }
    }

    private Set<ArtifactFormat> allowedFormats(ArtifactType type) {
        return switch (type) {
            case PROJECT_REPORT, DESIGN_SPEC, CUSTOM_DOCUMENT ->
                    Set.of(ArtifactFormat.MARKDOWN, ArtifactFormat.DOCX, ArtifactFormat.HTML,
                            ArtifactFormat.PPTX, ArtifactFormat.PDF);
            case DATABASE_DESIGN ->
                    Set.of(ArtifactFormat.SQL, ArtifactFormat.MARKDOWN, ArtifactFormat.DOCX,
                            ArtifactFormat.HTML, ArtifactFormat.PDF);
            case PRESENTATION -> Set.of(ArtifactFormat.PPTX);
        };
    }

    public GeneratedArtifactDTO toDto(GeneratedArtifact artifact) {
        List<String> sources = sourceFilesOf(artifact);
        int evidenceCount = evidenceOf(artifact).size();
        boolean previewed = artifact.getPreviewedAt() != null;
        boolean canApprove = previewed && artifact.getQualityStatus() != null
                && !artifact.getQualityStatus().equals("FAILED");
        return new GeneratedArtifactDTO(
                artifact.getId(), artifact.getProjectId(), artifact.getArtifactType(), artifact.getFormat(),
                artifact.getTitle(), artifact.getRelativePath(), sources, evidenceCount,
                artifact.getQualityStatus(), previewed, canApprove,
                artifact.getParentArtifactId(), artifact.getRootArtifactId(), artifact.getVersionNo(),
                artifact.getReviewStatus(),
                artifact.getReviewComment(), artifact.getFileSize(), artifact.getSha256(),
                artifact.getCreatedAt(), artifact.getReviewedAt(), artifact.getDeletedAt(),
                artifact.getOriginalRelativePath());
    }

    public List<String> sourceFilesOf(GeneratedArtifact artifact) {
        try {
            return MAPPER.readValue(artifact.getSourceFilesJson(), new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
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

    private GeneratedArtifactDTO writeArtifact(Project project,
                                               GeneratedArtifact parent,
                                               ArtifactType type,
                                               ArtifactFormat format,
                                               String title,
                                               String instructions,
                                               String requestedOutputDir,
                                               String requestedFileName,
                                               String content,
                                               List<String> sources,
                                               List<ArtifactEvidenceDTO> evidence,
                                               int versionNo,
                                               Long rootId) {
        Path root = Path.of(project.getRootPath()).toAbsolutePath().normalize();
        Path outputDir = resolveOutputDir(root, requestedOutputDir);
        Path target = null;
        try {
            String preparedContent = prepareContent(format, title, content);
            Files.createDirectories(outputDir);
            target = uniqueTarget(outputDir, requestedFileName, title, format);
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            try {
                documentWriter.write(temp, format, title, preparedContent, sources);
                List<String> formatChecks = fileValidator.validate(temp, format);
                ArtifactQualityDTO quality = qualityService.evaluate(
                        title, preparedContent, instructions, evidence, formatChecks);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);

                GeneratedArtifact artifact = new GeneratedArtifact();
                artifact.setProjectId(project.getId());
                artifact.setArtifactType(type.name());
                artifact.setFormat(format.name());
                artifact.setTitle(title.strip());
                artifact.setInstructions(blankToNull(instructions));
                artifact.setRelativePath(root.relativize(target).toString().replace('\\', '/'));
                artifact.setSourceFilesJson(MAPPER.writeValueAsString(sources));
                artifact.setEvidenceJson(MAPPER.writeValueAsString(evidence));
                artifact.setQualityJson(MAPPER.writeValueAsString(quality));
                artifact.setQualityStatus(quality.status());
                artifact.setContentText(preparedContent);
                artifact.setParentArtifactId(parent == null ? null : parent.getId());
                artifact.setRootArtifactId(rootId);
                artifact.setVersionNo(versionNo);
                artifact.setSha256(sha256(target));
                artifact.setFileSize(Files.size(target));
                artifact = artifactRepository.save(artifact);
                if (artifact.getRootArtifactId() == null) {
                    artifact.setRootArtifactId(artifact.getId());
                    artifact = artifactRepository.save(artifact);
                }
                return toDto(artifact);
            } catch (Exception moveOrWriteFailure) {
                Files.deleteIfExists(temp);
                throw moveOrWriteFailure;
            }
        } catch (BusinessException e) {
            deleteQuietly(target);
            throw e;
        } catch (Exception e) {
            deleteQuietly(target);
            throw new BusinessException(500, "生成文件失败: " + safeMessage(e));
        }
    }

    private List<ArtifactEvidenceDTO> evidenceItems(List<ScoredChunk> evidence,
                                                    Map<Long, FileAsset> fileMap) {
        List<ArtifactEvidenceDTO> items = new ArrayList<>();
        for (ScoredChunk item : evidence) {
            DocChunk chunk = item.chunk();
            FileAsset file = fileMap.get(chunk.getFileId());
            if (file == null || chunk.getContent() == null) {
                continue;
            }
            String excerpt = chunk.getContent().strip();
            if (excerpt.length() > 1200) {
                excerpt = excerpt.substring(0, 1200) + "…";
            }
            items.add(new ArtifactEvidenceDTO(chunk.getId(), file.getId(), file.getName(),
                    file.getRelPath(), chunk.getHeadingPath(), chunk.getPageNo(),
                    extractCellRange(excerpt), excerpt, item.score()));
        }
        return List.copyOf(items);
    }

    private String prepareContent(ArtifactFormat format, String title, String content) {
        String prepared = content == null ? "" : content.strip();
        if (format != ArtifactFormat.MARKDOWN) {
            return prepared;
        }
        String[] lines = prepared.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].strip().startsWith("# ")) {
                lines[index] = "# " + title.strip();
                return String.join("\n", lines).strip();
            }
        }
        return "# " + title.strip() + "\n\n" + prepared;
    }

    private String extractCellRange(String excerpt) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\[([^]\\r\\n]+![A-Z]+\\d+(?::[A-Z]+\\d+)?)\\]")
                .matcher(excerpt);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Preserve the original failure.
        }
    }

    private Path updateTarget(Path directory,
                              String requestedName,
                              ArtifactFormat format,
                              Path source) {
        String suffix = "." + format.getExtension();
        String base = requestedName.strip();
        if (base.toLowerCase().endsWith(suffix)) {
            base = base.substring(0, base.length() - suffix.length());
        }
        base = sanitizeFileName(base);
        if (base.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }
        Path target = directory.resolve(base + suffix).normalize();
        if (target.equals(source)) {
            return target;
        }
        if (Files.exists(target)) {
            throw new BusinessException(409, "目标文件已存在: " + target.getFileName());
        }
        return target;
    }

    private void moveFile(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void requireActive(GeneratedArtifact artifact) {
        if (artifact.getDeletedAt() != null) {
            throw new BusinessException(409, "交付物已在回收站，请先恢复");
        }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
