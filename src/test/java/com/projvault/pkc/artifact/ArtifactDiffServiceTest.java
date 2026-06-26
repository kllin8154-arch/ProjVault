package com.projvault.pkc.artifact;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactDiffServiceTest {

    @Test
    void comparesParentAndRevisionByParagraph() {
        GeneratedArtifact base = artifact(1L, null, "MARKDOWN");
        GeneratedArtifact target = artifact(2L, 1L, "MARKDOWN");
        ArtifactService artifacts = mock(ArtifactService.class);
        ArtifactPreviewService previews = mock(ArtifactPreviewService.class);
        when(artifacts.getEntity(1L)).thenReturn(base);
        when(artifacts.getEntity(2L)).thenReturn(target);
        when(previews.extractEditableText(base)).thenReturn("标题\n旧内容\n保留内容");
        when(previews.extractEditableText(target)).thenReturn("标题\n新内容\n保留内容");

        ArtifactDiffDTO result = new ArtifactDiffService(artifacts, previews).diff(2L, null);

        assertThat(result.lines()).containsExactly(
                new ArtifactDiffLine("UNCHANGED", "标题"),
                new ArtifactDiffLine("REMOVED", "旧内容"),
                new ArtifactDiffLine("ADDED", "新内容"),
                new ArtifactDiffLine("UNCHANGED", "保留内容"));
        assertThat(result.added()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.lines()).extracting(ArtifactDiffLine::text)
                .contains("旧内容", "新内容", "保留内容");
    }

    private GeneratedArtifact artifact(Long id, Long parentId, String format) {
        GeneratedArtifact artifact = new GeneratedArtifact();
        artifact.setId(id);
        artifact.setProjectId(9L);
        artifact.setParentArtifactId(parentId);
        artifact.setFormat(format);
        return artifact;
    }
}
