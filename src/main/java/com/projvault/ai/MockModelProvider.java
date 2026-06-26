package com.projvault.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock 模型实现：开发与单测专用，不发起任何外部调用。
 * 通过 projvault.ai.provider=mock 启用（默认）。
 * 切换真实模型时新增对应 Provider 实现类并修改配置，业务层零改动（决策 D1）。
 */
@Component
@Qualifier("systemRagAnswerProvider")
@ConditionalOnProperty(name = "projvault.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockModelProvider implements SummaryModelProvider, ExtractModelProvider,
        RagAnswerModelProvider, GraphModelProvider, EmbeddingProvider {

    private static final int DIMENSION = 8;

    @Override
    public String summarize(String docName, String content, String language) {
        int len = content == null ? 0 : content.length();
        return "[MOCK摘要] " + docName + "（正文 " + len + " 字符）";
    }

    @Override
    public List<ExtractedItem> extract(String docName, String context) {
        return List.of();
    }

    @Override
    public RagAnswer answer(String question, List<String> contextChunks) {
        return new RagAnswer("[MOCK回答] 资料中未找到依据：" + question, List.of(), false);
    }

    @Override
    public String generateGraphFragment(String batchContent, String language) {
        return "{\"nodes\":[],\"edges\":[]}";
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[DIMENSION];
        if (text != null && !text.isEmpty()) {
            for (int i = 0; i < text.length(); i++) {
                v[i % DIMENSION] += text.charAt(i) % 31;
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < DIMENSION; i++) {
                    v[i] /= (float) norm;
                }
            }
        }
        return v;
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }
}
