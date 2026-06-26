package com.projvault.pkc.artifact;

import com.projvault.ai.RagAnswer;
import com.projvault.common.BusinessException;
import com.projvault.pkc.file.DocChunk;
import com.projvault.pkc.file.DocChunkRepository;
import com.projvault.pkc.file.FileAsset;
import com.projvault.pkc.file.FileAssetRepository;
import com.projvault.pkc.rag.RagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArtifactRevisionService {

    private final ArtifactService artifactService;
    private final ArtifactPreviewService previewService;
    private final RagService ragService;
    private final DocChunkRepository chunkRepository;
    private final FileAssetRepository fileRepository;

    public ArtifactRevisionService(ArtifactService artifactService,
                                   ArtifactPreviewService previewService,
                                   RagService ragService,
                                   DocChunkRepository chunkRepository,
                                   FileAssetRepository fileRepository) {
        this.artifactService = artifactService;
        this.previewService = previewService;
        this.ragService = ragService;
        this.chunkRepository = chunkRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional
    public GeneratedArtifactDTO revise(Long artifactId, ReviseArtifactRequest request) {
        GeneratedArtifact parent = artifactService.getEntity(artifactId);
        String original = previewService.extractEditableText(parent);
        List<String> contexts = evidenceContexts(parent);
        contexts = new ArrayList<>(contexts);
        contexts.add(0, "【待修订原稿】\n" + truncate(original, 40_000));
        String prompt = "请在保留有证据内容的前提下修订交付物。只使用原稿和证据，"
                + "无法确认时明确写待确认，不得新增无依据的人名、金额、日期、接口或配置。\n"
                + "修改意见：" + request.getInstructions().strip();
        RagAnswer answer = ragService.answerFromContexts(prompt, contexts);
        if (answer == null || !answer.grounded() || answer.answer() == null || answer.answer().isBlank()) {
            throw new BusinessException(503, "模型未返回可信修订稿，原稿保持不变");
        }
        String title = request.getTitle() == null || request.getTitle().isBlank()
                ? parent.getTitle() : request.getTitle().strip();
        return artifactService.createDerived(parent, title, request.getInstructions(), answer.answer());
    }

    @Transactional
    public GeneratedArtifactDTO edit(Long artifactId, EditArtifactRequest request) {
        GeneratedArtifact parent = artifactService.getEntity(artifactId);
        String note = request.getComment() == null || request.getComment().isBlank()
                ? "在线轻量编辑" : "在线轻量编辑：" + request.getComment().strip();
        return artifactService.createDerived(
                parent, request.getTitle().strip(), note, request.getContent().strip());
    }

    private List<String> evidenceContexts(GeneratedArtifact artifact) {
        List<ArtifactEvidenceDTO> evidence = artifactService.evidenceOf(artifact);
        List<Long> chunkIds = evidence.stream().map(ArtifactEvidenceDTO::chunkId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, DocChunk> chunks = new LinkedHashMap<>();
        for (DocChunk chunk : chunkRepository.findAllById(chunkIds)) {
            chunks.put(chunk.getId(), chunk);
        }
        List<String> contexts = new ArrayList<>();
        for (ArtifactEvidenceDTO item : evidence) {
            DocChunk chunk = chunks.get(item.chunkId());
            if (chunk != null) {
                contexts.add("【来源：" + item.relPath() + location(item) + "】\n" + chunk.getContent());
            }
        }
        if (!contexts.isEmpty()) {
            return contexts;
        }
        for (String relPath : artifactService.sourceFilesOf(artifact)) {
            FileAsset file = fileRepository
                    .findByProjectIdAndRelPathAndDeletedFlagFalse(artifact.getProjectId(), relPath)
                    .orElse(null);
            if (file == null) {
                continue;
            }
            for (DocChunk chunk : chunkRepository.findByFileIdOrderBySeq(file.getId()).stream().limit(3).toList()) {
                contexts.add("【来源：" + relPath + "】\n" + chunk.getContent());
            }
        }
        return contexts;
    }

    private String location(ArtifactEvidenceDTO item) {
        if (item.cellRange() != null) {
            return " · " + item.cellRange();
        }
        if (item.pageNo() > 0) {
            return " · 第 " + item.pageNo() + " 页";
        }
        return item.headingPath() == null || item.headingPath().isBlank()
                ? "" : " · " + item.headingPath();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
