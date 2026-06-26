package com.projvault.pkc.rag;

import com.projvault.pkc.file.FileAsset;
import com.projvault.pkc.file.ProjectRelevanceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkRetrieverTest {

    private final ChunkRetriever retriever = new ChunkRetriever(null, null);

    @Test
    void normalizesColloquialQuestionSuffix() {
        assertThat(retriever.normalizeQuery("合同外变更有哪些？"))
                .isEqualTo("合同外变更");
        assertThat(retriever.normalizeQuery("合同外变更是什么"))
                .isEqualTo("合同外变更");
    }

    @Test
    void metadataNameHitOutranksBodyOnlyHit() {
        List<String> keywords = retriever.tokenize("合同外变更有哪些？");
        String core = retriever.normalizeQuery("合同外变更有哪些？");

        double nameHit = retriever.metadataScore(
                retriever.normalizeText("04合同外变更清单-v1.md 合同外变更清单"),
                retriever.normalizeText("04合同外变更清单-v1.md"),
                core,
                keywords);
        double bodyOnlyHit = retriever.metadataScore(
                retriever.normalizeText("项目经理工作台原型 点击合同外变更数量进入详情"),
                retriever.normalizeText("07原型-html-项目经理工作台.html"),
                core,
                keywords);

        assertThat(nameHit).isGreaterThan(bodyOnlyHit);
    }

    @Test
    void allowsFormalDeploymentQuestionsToUseMixedReferenceFiles() {
        FileAsset file = new FileAsset();
        file.setRelevanceStatus(ProjectRelevanceService.REFERENCE);

        assertThat(retriever.usableForRetrieval(file, "\u6b63\u5f0f\u90e8\u7f72\u6570\u636e\u5e93\u5730\u5740\u662f\u4ec0\u4e48\uff1f")).isTrue();
        assertThat(retriever.usableForRetrieval(file, "\u5408\u540c\u8303\u56f4\u662f\u4ec0\u4e48\uff1f")).isFalse();
    }

    @Test
    void keepsSingleCanonicalFileForExactContentDuplicates() {
        FileAsset canonical = new FileAsset();
        canonical.setId(1L); canonical.setRelPath("01合同范围-终版.md"); canonical.setSha256("same"); canonical.setRelevanceScore(0.9);
        FileAsset duplicate = new FileAsset();
        duplicate.setId(2L); duplicate.setRelPath("临时收件/合同范围-终版副本.md"); duplicate.setSha256("same"); duplicate.setRelevanceScore(0.7);

        assertThat(retriever.canonicalFiles(List.of(duplicate, canonical)))
                .extracting(FileAsset::getId).containsExactly(1L);
    }
}
