package com.projvault.pkc.eval;

import com.projvault.ai.RagCitation;
import com.projvault.pkc.rag.AskTrace;
import com.projvault.pkc.rag.RagAnswerWithTrace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationRunnerTest {
    private final EvaluationRunner runner = new EvaluationRunner(null, null, null, null);

    @Test
    void requiresAnswerPointsAndEvidenceSources() {
        GoldenQuestion question = new GoldenQuestion();
        question.setId(1L);
        question.setQuestion("最高合同风险是什么");
        question.setExpectedKeywords("RISK-B2,补充协议,暂停验收");
        question.setExpectedSourcePatterns("合同外变更清单,会议纪要");
        RagAnswerWithTrace answer = new RagAnswerWithTrace(
                "当前为 RISK-B2，补充协议待签，因此暂停验收。",
                List.of(new RagCitation("合同外变更清单-v2.md", "风险", 10L, 0.9),
                        new RagCitation("会议纪要-0618.md", "决议", 11L, 0.8)), true,
                AskTrace.empty("standard"));

        EvaluationItemResult result = runner.score(question, answer);

        assertThat(result.passed()).isTrue();
        assertThat(result.keywordCoverage()).isEqualTo(1.0);
        assertThat(result.sourceCoverage()).isEqualTo(1.0);
    }

    @Test
    void failsPlausibleAnswerWithoutRequiredSource() {
        GoldenQuestion question = new GoldenQuestion();
        question.setExpectedKeywords("RISK-B2,暂停验收");
        question.setExpectedSourcePatterns("合同外变更清单,会议纪要");
        RagAnswerWithTrace answer = new RagAnswerWithTrace("RISK-B2，暂停验收。",
                List.of(new RagCitation("项目原型.md", null, 12L, 0.7)), true, AskTrace.empty("standard"));

        assertThat(runner.score(question, answer).passed()).isFalse();
    }
}
