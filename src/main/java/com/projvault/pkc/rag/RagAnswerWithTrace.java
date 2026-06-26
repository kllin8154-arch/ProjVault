package com.projvault.pkc.rag;

import com.projvault.ai.RagAnswer;
import com.projvault.ai.RagCitation;

import java.util.List;

public record RagAnswerWithTrace(
        String answer,
        List<RagCitation> citations,
        boolean grounded,
        AskTrace trace) {

    public static RagAnswerWithTrace from(RagAnswer answer, AskTrace trace) {
        return new RagAnswerWithTrace(answer.answer(), answer.citations(), answer.grounded(), trace);
    }
}
