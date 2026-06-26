package com.projvault.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude Provider（claude-3-* / claude-opus-* 等）。
 *
 * application.yml 配置示例：
 * <pre>
 * projvault:
 *   ai:
 *     provider: anthropic
 *     anthropic:
 *       api-key: sk-ant-xxx
 *       model: claude-3-haiku-20240307
 *       max-tokens: 2048
 *       timeout-seconds: 60
 * </pre>
 */
@Component
@Qualifier("systemRagAnswerProvider")
@ConditionalOnProperty(name = "projvault.ai.provider", havingValue = "anthropic")
public class AnthropicProvider implements SummaryModelProvider, ExtractModelProvider,
        RagAnswerModelProvider, GraphModelProvider, EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int EMBEDDING_DIM = 8;

    @Value("${projvault.ai.anthropic.api-key:}")
    private String apiKey;

    @Value("${projvault.ai.anthropic.model:claude-3-haiku-20240307}")
    private String model;

    @Value("${projvault.ai.anthropic.max-tokens:2048}")
    private int maxTokens;

    @Value("${projvault.ai.anthropic.timeout-seconds:60}")
    private int timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ── RagAnswerModelProvider ────────────────────────────────────────────────

    @Override
    public RagAnswer answer(String question, List<String> contextChunks) {
        if (!isConfigured()) {
            return new RagAnswer("模型 API Key 未配置，无法回答。", List.of(), false);
        }
        try {
            String context = String.join("\n\n---\n\n", contextChunks);
            String userMsg = "以下是相关项目资料片段：\n\n" + context
                    + "\n\n---\n\n用户问题：" + question;
            String system = "你是一个项目文档助手。请仅基于提供的项目资料回答问题，用中文回答。"
                    + "若资料中没有相关依据，请明确说明资料中未找到相关内容，不要编造信息。";
            String text = chat(system, userMsg);
            return new RagAnswer(text, List.of(), true);
        } catch (Exception e) {
            log.error("[anthropic] RAG 调用失败: {}", e.getMessage());
            return new RagAnswer("模型调用失败：" + e.getMessage(), List.of(), false);
        }
    }

    // ── SummaryModelProvider ─────────────────────────────────────────────────

    @Override
    public String summarize(String docName, String content, String language) {
        if (!isConfigured()) {
            int len = content == null ? 0 : content.length();
            return "[摘要不可用] 未配置 API Key（" + docName + "，" + len + " 字符）";
        }
        try {
            String snippet = content != null && content.length() > 2000
                    ? content.substring(0, 2000) + "..." : content;
            String userMsg = "文档名：" + docName + "\n\n" + snippet;
            String system = "请为以下技术文档生成一段中文摘要，不超过150字，"
                    + "提炼核心功能、配置项或关键信息，不要重复文档名。";
            return chat(system, userMsg);
        } catch (Exception e) {
            log.error("[anthropic] 摘要调用失败: {}", e.getMessage());
            return "[摘要生成失败] " + e.getMessage();
        }
    }

    // ── 不用于真实流程的接口（占位实现）──────────────────────────────────────

    @Override
    public List<ExtractedItem> extract(String docName, String context) {
        return List.of();
    }

    @Override
    public String generateGraphFragment(String batchContent, String language) {
        return "{\"nodes\":[],\"edges\":[]}";
    }

    @Override
    public float[] embed(String text) {
        return new float[EMBEDDING_DIM];
    }

    @Override
    public int dimension() {
        return EMBEDDING_DIM;
    }

    // ── 内部 HTTP 实现 ────────────────────────────────────────────────────────

    /**
     * 调用 Anthropic Messages API，返回回答文本。
     */
    private String chat(String system, String userContent) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userContent));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        body.put("messages", messages);

        String reqJson = MAPPER.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(MESSAGES_URL))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = MAPPER.readTree(resp.body());
        return root.path("content").get(0).path("text").asText();
    }

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
