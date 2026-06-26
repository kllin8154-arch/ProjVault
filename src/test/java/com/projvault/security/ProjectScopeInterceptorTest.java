package com.projvault.security;

import com.projvault.pkc.artifact.ArtifactFolderRepository;
import com.projvault.pkc.artifact.GeneratedArtifactRepository;
import com.projvault.pkc.eval.EvaluationRunRepository;
import com.projvault.pkc.eval.GoldenQuestionRepository;
import com.projvault.pkc.file.ConfigItemRepository;
import com.projvault.pkc.file.DocChunkRepository;
import com.projvault.pkc.file.DocFamilyRepository;
import com.projvault.pkc.file.DocImageRepository;
import com.projvault.pkc.file.FileAsset;
import com.projvault.pkc.file.FileAssetRepository;
import com.projvault.pkc.scan.ScanTaskRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectScopeInterceptorTest {
    private final ProjectAccessService access = mock(ProjectAccessService.class);
    private final FileAssetRepository files = mock(FileAssetRepository.class);
    private final ProjectScopeInterceptor interceptor = new ProjectScopeInterceptor(
            access,
            mock(ScanTaskRepository.class),
            files,
            mock(DocChunkRepository.class),
            mock(DocImageRepository.class),
            mock(ConfigItemRepository.class),
            mock(DocFamilyRepository.class),
            mock(GeneratedArtifactRepository.class),
            mock(ArtifactFolderRepository.class),
            mock(GoldenQuestionRepository.class),
            mock(EvaluationRunRepository.class));

    @Test
    void nestedProjectPathRequiresProjectOwnership() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/pkc/projects/38/config-items");

        interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        verify(access).requireProject(request, 38L);
    }

    @Test
    void directFilePathResolvesItsProject() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/pkc/files/77/raw");
        FileAsset file = new FileAsset();
        file.setProjectId(38L);
        when(files.findById(77L)).thenReturn(Optional.of(file));

        interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        verify(access).requireProject(request, 38L);
    }
}
