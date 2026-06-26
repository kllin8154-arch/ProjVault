package com.projvault.pkc.rag;

import com.projvault.pkc.file.DocChunk;
import com.projvault.pkc.file.FileAsset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRerankerTest {

    @Test
    void semanticCandidateCanRecoverWithoutLexicalHit() {
        FileAsset file = new FileAsset();
        file.setId(1L);
        file.setRelPath("04合同/变更清单.md");
        file.setSummary("移动端离线填报属于合同范围之外的新增需求");
        DocChunk chunk = new DocChunk();
        chunk.setId(11L);
        chunk.setFileId(1L);
        chunk.setContent("该功能尚未签署补充协议，当前不得纳入验收。 ");

        List<ScoredChunk> result = new HybridReranker().rerank(
                "离线填报能否进入验收", List.of("离线填报", "验收"),
                Map.of(), Map.of(), List.of(chunk), Map.of(1L, file), 5);

        assertThat(result).extracting(item -> item.chunk().getId()).containsExactly(11L);
        assertThat(result.get(0).score()).isGreaterThan(0.1);
    }

    @Test
    void capsChunksFromSameFile() {
        FileAsset file = new FileAsset();
        file.setId(1L);
        file.setRelPath("风险.md");
        List<DocChunk> chunks = java.util.stream.IntStream.range(0, 6).mapToObj(index -> {
            DocChunk chunk = new DocChunk();
            chunk.setId((long) index + 1);
            chunk.setFileId(1L);
            chunk.setContent("风险编号 RISK-" + index + " 复核日期");
            return chunk;
        }).toList();

        List<ScoredChunk> result = new HybridReranker().rerank(
                "风险编号和复核日期", List.of("风险编号", "复核日期"),
                Map.of(), Map.of(), chunks, Map.of(1L, file), 10);

        assertThat(result).hasSize(3);
    }
}
