package com.projvault.pkc.artifact;

import com.projvault.common.BusinessException;
import com.projvault.pkc.file.DocChunk;
import com.projvault.pkc.file.FileAsset;
import com.projvault.pkc.rag.ScoredChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactEvidenceScopeTest {

    private final ArtifactEvidenceScope scope = new ArtifactEvidenceScope();

    @Test
    void keepsOnlyExplicitlyNumberedFiles() {
        List<FileAsset> files = List.of(
                file(18L, "18增量局部替换实验.md"),
                file(19L, "19增量多文件实验.md"),
                file(17L, "17增量替换闭环实验.md"));
        List<ScoredChunk> evidence = List.of(
                evidence(18L, 1.0), evidence(19L, 0.8), evidence(17L, 0.75));

        List<ScoredChunk> result = scope.apply(
                "仅使用18、19号增量实验资料，不引用其他文件。", evidence, files);

        assertThat(result).extracting(item -> item.chunk().getFileId())
                .containsExactly(18L, 19L);
    }

    @Test
    void failsClosedWhenExclusiveSourceDoesNotExist() {
        assertThatThrownBy(() -> scope.apply(
                "只依据99号文件。", List.of(evidence(18L, 1.0)),
                List.of(file(18L, "18实验.md"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("限定的来源文件");
    }

    @Test
    void leavesEvidenceUntouchedWithoutExclusiveLanguage() {
        List<ScoredChunk> evidence = List.of(evidence(1L, 1.0), evidence(2L, 0.5));
        assertThat(scope.apply("编写项目周报。", evidence, List.of())).isSameAs(evidence);
    }

    private FileAsset file(long id, String relPath) {
        FileAsset file = new FileAsset();
        file.setId(id);
        file.setRelPath(relPath);
        file.setName(relPath);
        return file;
    }

    private ScoredChunk evidence(long fileId, double score) {
        DocChunk chunk = new DocChunk();
        chunk.setFileId(fileId);
        chunk.setContent("证据");
        return new ScoredChunk(chunk, score);
    }
}
