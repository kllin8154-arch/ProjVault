package com.projvault.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class UserConversationClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public RagAnswer answer(ConversationAiConfig config, String question, List<String> contextChunks) {
        try {
            String context = String.join("\n\n---\n\n", contextChunks);
            String userMessage = "以下是相关项目资料片段：\n\n" + context
                    + "\n\n---\n\n用户问题：" + question;
            String systemMessage = "你是一个项目文档助手。请仅基于提供的项目资料回答问题，用中文回答。"
                    + "先回答可确认的信息，再单独说明无法确认的信息；不要编造资料中不存在的结论。"
                    + "涉及清单、差异、合同外、新增或变更时，必须标注证据和判断依据。";
            return new RagAnswer(chat(config, systemMessage, userMessage), List.of(), true);
        } catch (Exception e) {
            return new RagAnswer("个人对话模型调用失败：" + safeMessage(e), List.of(), false);
        }
    }

    public String test(ConversationAiConfig config) {
        try {
            return chat(config, "你是连接测试助手。", "只回复两个字：正常");
        } catch (Exception e) {
            throw new IllegalStateException("连接失败：" + safeMessage(e));
        }
    }

    private String chat(ConversationAiConfig config, String system, String userContent) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", userContent));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", messages);
        body.put("max_tokens", 4096);
        body.put("temperature", 0.3);

        String url = config.baseUrl().endsWith("/")
                ? config.baseUrl() + "chat/completions"
                : config.baseUrl() + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("模型服务返回 HTTP " + response.statusCode());
        }
        JsonNode root = MAPPER.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new IllegalStateException("模型服务未返回有效回答");
        }
        return content.asText();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.replaceAll("(?i)(bearer|api[-_ ]?key)\\s+[^\\s,;]+", "$1 ***");
    }
}
