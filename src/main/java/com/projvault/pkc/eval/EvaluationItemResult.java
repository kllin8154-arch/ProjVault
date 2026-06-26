package com.projvault.pkc.eval;

import java.util.List;

public record EvaluationItemResult(
        Long questionId,
        String question,
        boolean passed,
        double score,
        double keywordCoverage,
        double sourceCoverage,
        boolean grounded,
        List<String> missingKeywords,
        List<String> matchedSources,
        String answer) {
}
