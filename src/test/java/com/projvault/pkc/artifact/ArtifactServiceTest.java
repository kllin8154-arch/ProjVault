package com.projvault.pkc.artifact;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesGroundedDraftInsideReviewDirectory() throws Exception {
        Project project = new Project();
        project.setId(1L);
        project.setName("测试项目");
        project.setRootPath(tempDir.toString());

        FileAsset file = new FileAsset();
        file.setId(7L);
        file.setRelPath("01合同/项目范围.md");
        file.setName("项目范围.md");
        DocChunk chunk = new DocChunk();
        chunk.setId(8L);
        chunk.setFileId(7L);
        chunk.setContent("合同范围包含资料归档和项目报告。");

        ProjectRepository projects = mock(ProjectRepository.class);
        GeneratedArtifactRepository artifacts = mock(GeneratedArtifactRepository.class);
        ChunkRetriever retriever = mock(ChunkRetriever.class);
        FileAssetRepository files = mock(FileAssetRepository.class);
        RagService rag = mock(RagService.class);
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(retriever.retrieve(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(new ScoredChunk(chunk, 1.0)));
        when(files.findAllById(anyList())).thenReturn(List.of(file));
        when(rag.answerFromContexts(anyString(), anyList()))
                .thenReturn(new RagAnswer("# 模型返回的旧标题\n- 已完成范围确认", List.of(), true));
        when(artifacts.save(any(GeneratedArtifact.class))).thenAnswer(invocation -> {
            GeneratedArtifact artifact = invocation.getArgument(0);
            artifact.setId(99L);
            return artifact;
        });

        ArtifactService service = new ArtifactService(
                projects, artifacts, retriever, files, rag,
                new ArtifactDocumentWriter(), new ArtifactContentValidator(),
                new ArtifactFileValidator(), new ArtifactQualityService(),
                new ArtifactEvidenceScope(), mock(ArtifactFolderService.class));
        GenerateArtifactRequest request = new GenerateArtifactRequest();
        request.setTitle("项目周报");
        request.setArtifactType(ArtifactType.PROJECT_REPORT);
        request.setFormat(ArtifactFormat.MARKDOWN);
        request.setOutputDir("AI交付物/待审查/周报");

        GeneratedArtifactDTO result = service.generate(1L, request);

        Path generated = tempDir.resolve(result.relativePath());
        assertThat(generated).isRegularFile();
        assertThat(result.reviewStatus()).isEqualTo("DRAFT");
        assertThat(result.sourceFiles()).containsExactly("01合同/项目范围.md");
        assertThat(Files.readString(generated))
                .startsWith("# 项目周报")
                .contains("资料来源", "项目范围.md");
    }

    @Test
    void approvalRequiresPreviewButRejectionDoesNot() {
        GeneratedArtifact artifact = new GeneratedArtifact();
        artifact.setId(10L);
        artifact.setProjectId(1L);
        artifact.setReviewStatus("DRAFT");
        artifact.setQualityStatus("PASSED");
        GeneratedArtifactRepository artifacts = mock(GeneratedArtifactRepository.class);
        when(artifacts.findById(10L)).thenReturn(Optional.of(artifact));
        when(artifacts.save(any(GeneratedArtifact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArtifactService service = new ArtifactService(
                mock(ProjectRepository.class), artifacts, mock(ChunkRetriever.class),
                mock(FileAssetRepository.class), mock(RagService.class),
                new ArtifactDocumentWriter(), new ArtifactContentValidator(),
                new ArtifactFileValidator(), new ArtifactQualityService(),
                new ArtifactEvidenceScope(), mock(ArtifactFolderService.class));
        ReviewArtifactRequest approve = new ReviewArtifactRequest();
        approve.setStatus("APPROVED");

        assertThatThrownBy(() -> service.review(10L, approve))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("在线预览");

        artifact.setPreviewedAt(LocalDateTime.now());
        GeneratedArtifactDTO approved = service.review(10L, approve);
        assertThat(approved.reviewStatus()).isEqualTo("APPROVED");
    }

    @Test
    void movesSoftDeletesAndRestoresPhysicalArtifact() throws Exception {
        Project project = new Project();
        project.setId(1L);
        project.setRootPath(tempDir.toString());
        Path original = tempDir.resolve("AI交付物/待审查/原稿.md");
        Files.createDirectories(original.getParent());
        Files.writeString(original, "# 原稿");
        GeneratedArtifact artifact = new GeneratedArtifact();
        artifact.setId(12L);
        artifact.setProjectId(1L);
        artifact.setTitle("原稿");
        artifact.setFormat(ArtifactFormat.MARKDOWN.name());
        artifact.setRelativePath("AI交付物/待审查/原稿.md");
        artifact.setReviewStatus("DRAFT");
        artifact.setVersionNo(1);

        ProjectRepository projects = mock(ProjectRepository.class);
        GeneratedArtifactRepository artifacts = mock(GeneratedArtifactRepository.class);
        ArtifactFolderService folders = mock(ArtifactFolderService.class);
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(artifacts.findById(12L)).thenReturn(Optional.of(artifact));
        when(artifacts.save(any(GeneratedArtifact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(folders.folderPath(1L, 5L)).thenReturn("AI交付物/待审查/周报");
        ArtifactService service = new ArtifactService(projects, artifacts, mock(ChunkRetriever.class),
                mock(FileAssetRepository.class), mock(RagService.class), new ArtifactDocumentWriter(),
                new ArtifactContentValidator(), new ArtifactFileValidator(), new ArtifactQualityService(),
                new ArtifactEvidenceScope(), folders);
        ArtifactMoveRequest move = new ArtifactMoveRequest();
        move.setTitle("项目周报");
        move.setFileName("第24周");
        move.setFolderId(5L);

        GeneratedArtifactDTO moved = service.move(12L, move);
        assertThat(Files.readString(tempDir.resolve(moved.relativePath()))).isEqualTo("# 原稿");
        assertThat(original).doesNotExist();

        GeneratedArtifactDTO deleted = service.delete(12L);
        assertThat(deleted.deletedAt()).isNotNull();
        assertThat(tempDir.resolve(deleted.relativePath())).exists();

        GeneratedArtifactDTO restored = service.restore(12L);
        assertThat(restored.deletedAt()).isNull();
        assertThat(Files.readString(tempDir.resolve(restored.relativePath()))).isEqualTo("# 原稿");
    }
}
