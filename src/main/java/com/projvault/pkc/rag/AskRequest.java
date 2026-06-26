package com.projvault.pkc.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RAG 问答请求 DTO。
 */
public class AskRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过 500 字")
    private String question;

    /** 最多返回多少个来源 chunk，默认 5，最大 20 */
    private int topK = 5;

    /** 检索模式：standard（默认）| local（图谱邻域增强） */
    private String mode = "standard";

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = Math.max(1, Math.min(20, topK));
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = (mode == null || mode.isBlank()) ? "standard" : mode.trim().toLowerCase();
    }
}
