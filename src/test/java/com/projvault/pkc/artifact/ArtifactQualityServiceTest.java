package com.projvault.pkc.artifact;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactQualityServiceTest {

    private final ArtifactQualityService service = new ArtifactQualityService();

    @Test
    void passesWhenHighRiskFactsExistInEvidence() {
        ArtifactEvidenceDTO evidence = new ArtifactEvidenceDTO(
                1L, 2L, "计划.md", "计划.md", "里程碑", 0, null,
                "预算 820000 元，复核日期为 2026-09-30。", 1.0);

        ArtifactQualityDTO quality = service.evaluate(
                "项目报告", "预算 820000 元，复核日期为 2026-09-30。",
                "", List.of(evidence), List.of("格式有效"));

        assertThat(quality.status()).isEqualTo("PASSED");
        assertThat(quality.evidenceCoverageScore()).isEqualTo(100);
        assertThat(quality.suspectedUnsupportedFacts()).isEmpty();
    }

    @Test
    void warnsWhenAmountIsNotSupportedByEvidence() {
        ArtifactEvidenceDTO evidence = new ArtifactEvidenceDTO(
                1L, 2L, "计划.md", "计划.md", null, 0, null,
                "项目预算尚待确认。", 1.0);

        ArtifactQualityDTO quality = service.evaluate(
                "项目报告", "项目预算为 990000 元。",
                "", List.of(evidence), List.of("格式有效"));

        assertThat(quality.status()).isEqualTo("WARNING");
        assertThat(quality.suspectedUnsupportedFacts()).contains("990000 元");
    }

    @Test
    void flagsStrongInferenceThatIsNotPresentInEvidence() {
        ArtifactEvidenceDTO evidence = new ArtifactEvidenceDTO(
                1L, 2L, "批次.md", "批次.md", null, 0, null,
                "复核日期为 2026-09-30，生效日期为 2026-10-30。", 1.0);

        ArtifactQualityDTO quality = service.evaluate(
                "核验单", "日期先后关系证明流转合规，不存在倒签风险。",
                "核对日期", List.of(evidence), List.of("格式有效"));

        assertThat(quality.status()).isEqualTo("WARNING");
        assertThat(quality.suspectedUnsupportedFacts())
                .anyMatch(item -> item.contains("证明流转合规"))
                .anyMatch(item -> item.contains("不存在倒签风险"));
    }

    @Test
    void countsOnePendingItemPerContentLine() {
        ArtifactQualityDTO quality = service.evaluate(
                "核验单", "## 待确认项\n> 待确认项：需要补充审批记录，当前标记为待确认。",
                "", List.of(), List.of("格式有效"));

        assertThat(quality.pendingCount()).isEqualTo(1);
    }

    @Test
    void failsWhenContentContainsMojibakeControls() {
        ArtifactQualityDTO quality = service.evaluate(
                "核验单", "# äº¤ä»\u0085®ç©\næ\u0087æ¡£å\u0085å®¹",
                "", List.of(), List.of("格式有效"));

        assertThat(quality.status()).isEqualTo("FAILED");
        assertThat(quality.warnings()).anyMatch(item -> item.contains("编码损坏"));
    }
}
