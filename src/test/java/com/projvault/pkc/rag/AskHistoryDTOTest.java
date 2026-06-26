package com.projvault.pkc.rag;

import com.projvault.ai.RagCitation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskHistoryDTOTest {

    @Test
    void hidesRetrievedCandidatesForUngroundedHistory() {
        AskHistory history = new AskHistory();
        history.setGrounded(false);
        history.setCitationCount(2);
        history.setCitationsJson("""
                [
                  {"fileName":"candidate.md","headingPath":null,"chunkId":1,"score":0.4},
                  {"fileName":"another.md","headingPath":null,"chunkId":2,"score":0.3}
                ]
                """);

        AskHistoryDTO dto = AskHistoryDTO.from(history);

        assertThat(dto.getCitationCount()).isZero();
        assertThat(dto.getCitations()).isEmpty();
    }

    @Test
    void labelsLegacyBlankAnswerAsModelFailure() {
        AskHistory history = new AskHistory();
        history.setGrounded(false);
        history.setAnswer("");

        AskHistoryDTO dto = AskHistoryDTO.from(history);

        assertThat(dto.getAnswer()).contains("回答模型暂时不可用");
        assertThat(dto.isGrounded()).isFalse();
        assertThat(dto.getCitationCount()).isZero();
    }

    @Test
    void keepsOnlyPrimaryOrExplicitlyNamedSources() {
        AskHistory history = new AskHistory();
        history.setGrounded(true);
        history.setCitationCount(3);
        history.setCitationsJson("""
                [
                  {"fileName":"evidence.md","headingPath":null,"chunkId":1,"score":1.0},
                  {"fileName":"candidate.md","headingPath":null,"chunkId":2,"score":0.6},
                  {"fileName":"weak.md","headingPath":null,"chunkId":3,"score":0.2}
                ]
                """);

        AskHistoryDTO dto = AskHistoryDTO.from(history);

        assertThat(dto.getCitationCount()).isOne();
        assertThat(dto.getCitations()).extracting(citation -> citation.fileName())
                .containsExactly("evidence.md");
    }

    @Test
    void keepsNamedLowScoreSourceAndDeduplicatesChunksByFile() {
        AskHistory history = new AskHistory();
        history.setGrounded(true);
        history.setCitationCount(4);
        history.setAnswer("结论同时来源于《额外功能清单.xlsx》。");
        history.setCitationsJson("""
                [
                  {"fileName":"正式清单.xlsx","headingPath":"Sheet1","chunkId":1,"score":1.0},
                  {"fileName":"正式清单.xlsx","headingPath":"Sheet2","chunkId":2,"score":0.8},
                  {"fileName":"04项目执行阶段/额外功能清单.xlsx","headingPath":"Sheet1","chunkId":3,"score":0.2},
                  {"fileName":"无关候选.xlsx","headingPath":"Sheet1","chunkId":4,"score":0.2}
                ]
                """);

        AskHistoryDTO dto = AskHistoryDTO.from(history);

        assertThat(dto.getCitationCount()).isEqualTo(2);
        assertThat(dto.getCitations()).extracting(RagCitation::fileName)
                .containsExactly(
                        "正式清单.xlsx",
                        "04项目执行阶段/额外功能清单.xlsx");
        assertThat(dto.getCitations().get(0).chunkId()).isEqualTo(1L);
    }
}
