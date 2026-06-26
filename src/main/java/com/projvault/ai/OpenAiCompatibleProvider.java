package com.projvault.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projvault.settings.SettingService;
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
 * OpenAI 兼容格式 Provider（适用于 DeepSeek / OpenAI / Qwen / Ollama / Mimo 等）。
 *
 * application.yml 配置示例：
 * <pre>
 * projvault:
 *   ai:
 *     provider: openai-compatible
 *     openai-compatible:
 *       base-url: https://api.deepseek.com/v1   # 或 https://api.openai.com/v1 等
 *       api-key: sk-xxx
 *       model: deepseek-chat
 *       max-tokens: 2048
 *       temperature: 0.3
 *       timeout-seconds: 60
 * </pre>
 *
 * 切换模型只需修改 base-url / api-key / model，业务层零改动（决策 D1）。
 */
@Component
@Qualifier("systemRagAnswerProvider")
@ConditionalOnProperty(name = "projvault.ai.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleProvider implements SummaryModelProvider, ExtractModelProvider,
        RagAnswerModelProvider, GraphModelProvider, EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int EMBEDDING_DIM = 8;

    @Value("${projvault.ai.openai-compatible.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${projvault.ai.openai-compatible.api-key:}")
    private String apiKey;

    @Value("${projvault.ai.openai-compatible.model:deepseek-chat}")
    private String model;

    @Value("${projvault.ai.openai-compatible.max-tokens:2048}")
    private int maxTokens;

    @Value("${projvault.ai.openai-compatible.temperature:0.3}")
    private double temperature;

    @Value("${projvault.ai.openai-compatible.timeout-seconds:60}")
    private int timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final SettingService settingService;

    public OpenAiCompatibleProvider(SettingService settingService) {
        this.settingService = settingService;
    }

    private String effBaseUrl() { return settingService.get("ai.base-url", baseUrl); }
    private String effApiKey()  { return settingService.get("ai.api-key", apiKey); }
    private String effModel()   { return settingService.get("ai.model", model); }
    private int effTimeout() {
        try { return Integer.parseInt(settingService.get("ai.timeout", String.valueOf(timeoutSeconds))); }
        catch (NumberFormatException e) { return timeoutSeconds; }
    }

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
                    + "如果资料提供了间接线索或局部证据，也要先回答“可确认的信息”，再单独说明“无法确认的信息”。"
                    + "只有在提供的片段完全没有相关线索时，才说明：资料中未找到相关内容。不要因为缺少完整边界定义而整体拒答。"
                    + "当用户询问清单、差异、合同外、新增、变更、功能点等问题时，请根据“新增、补充、调整、变更、原型补充、招标/合同范围”等证据归纳，并标注判断依据。"
                    + "不要编造资料中不存在的项目、条款或功能。"
                    + "务必严格区分因果方向与相反/对立的概念：例如'谐振'与'反向谐振(180度反相)'是相反的，"
                    + "前者是致因、后者是对冲手段，绝不可混为一谈或缝合在一句里；保持事件先后与因果链准确。";
            String text = chat(system, userMsg);
            return new RagAnswer(text, List.of(), true);
        } catch (Exception e) {
            log.error("[openai-compat] RAG 调用失败: {}", e.getMessage());
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
            String system = "请为以下内容生成一段中文摘要，不超过180字，提炼核心实体、关键信息与它们的关系。"
                    + "若含相反/对立或因果关系，必须保留其方向与对立性，不要把相反的概念合并描述；不要重复标题名。";
            return chat(system, userMsg);
        } catch (Exception e) {
            log.error("[openai-compat] 摘要调用失败: {}", e.getMessage());
            return "[摘要生成失败] " + e.getMessage();
        }
    }

    // ── ExtractModelProvider（§9 第二级：LLM 判真伪 + 结构化）──────────────────

    @Override
    public List<ExtractedItem> extract(String docName, String context) {
        if (!isConfigured() || context == null || context.isBlank()) {
            return List.of();
        }
        try {
            String system = "你是项目配置抽取助手。从文档段落中识别**真实的部署/运行配置**："
                    + "服务器IP、端口、数据库连接串、部署路径、账号、URL、中间件版本等。"
                    + "必须忽略（这些不算真实配置）：文档章节层级编号（如1.1.1.1）；"
                    + "出现在 JSON 示例对象/字段说明/'如:'/'例如'/'举例'/IP白名单或黑名单示例 语境中的值（如192.168.1.1、10.0.0.0/24）；"
                    + "头像/avatar/图片/logo 等 URL（如 .../avatar1.jpg）；第三方参考网址（百科、政务公开网址等）；JSON字段名与纯说明性数字。"
                    + "只输出一个JSON数组，元素形如 "
                    + "{\"itemType\":\"ip|port|path|url|account|param|version\",\"keyName\":\"用途\",\"value\":\"原始值\",\"envHint\":null,\"serverHint\":null,\"confidence\":0.0}。"
                    + "没有真实配置则输出[]。不要输出任何额外说明或代码块标记。";
            String snippet = context.length() > 1500 ? context.substring(0, 1500) : context;
            String resp = chat(system, "文档：" + docName + "\n\n段落：\n" + snippet);
            return parseExtracted(resp);
        } catch (Exception e) {
            log.warn("[openai-compat] 配置抽取调用失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ExtractedItem> parseExtracted(String resp) {
        if (resp == null) {
            return List.of();
        }
        int lb = resp.indexOf('[');
        int rb = resp.lastIndexOf(']');
        if (lb < 0 || rb < lb) {
            return List.of();
        }
        List<ExtractedItem> out = new ArrayList<>();
        try {
            JsonNode arr = MAPPER.readTree(resp.substring(lb, rb + 1));
            if (!arr.isArray()) {
                return List.of();
            }
            for (JsonNode n : arr) {
                String value = n.path("value").asText("").strip();
                if (value.isEmpty()) {
                    continue;
                }
                out.add(new ExtractedItem(
                        n.path("itemType").asText(""),
                        n.path("keyName").asText(""),
                        value,
                        n.path("envHint").isNull() ? null : n.path("envHint").asText(null),
                        n.path("serverHint").isNull() ? null : n.path("serverHint").asText(null),
                        n.path("confidence").asDouble(0.0)));
            }
        } catch (Exception e) {
            log.warn("[openai-compat] 抽取结果 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
        return out;
    }

    // ── GraphModelProvider（暂不使用，返回空片段）────────────────────────────

    @Override
    public String generateGraphFragment(String batchContent, String language) {
        if (!isConfigured() || batchContent == null || batchContent.isBlank()) {
            return "{\"nodes\":[],\"edges\":[]}";
        }
        try {
            String system = "你是知识图谱抽取器。从给定文本片段中抽取**实体**与它们之间的**关系**。"
                    + "实体type用中文（如 人物/组织/地点/系统/服务/实物/概念/事件/技术/配置 等）。"
                    + "严禁把以下内容单独抽为实体节点：宽泛日期或时间戳（如 2026年、6月15日、22:14）、"
                    + "通用时间副词（如 凌晨、当天晚上、十年前）、空泛的时间段/场景描写（如 '地下室时期'）。"
                    + "这类时间/泛化信息只能作为相关事件或行为实体的 desc 描述，不要建独立节点、不要连边。"
                    + "优先抽取具名人物 / 具体实物设备 / 具名地点机构 / 具体事件 / 技术或配置概念。"
                    + "关系要具体（如 部署于/依赖/调用/属于/负责/连接/对应/对冲/导致 等），并给0~1的weight；"
                    + "对'相反/对立/对冲'类关系（如 A 抵消 B），weight 取较高值并在 desc 中点明其对立方向。"
                    + "只输出一个JSON对象，形如 "
                    + "{\"nodes\":[{\"name\":\"\",\"type\":\"\",\"desc\":\"\"}],"
                    + "\"edges\":[{\"source\":\"\",\"target\":\"\",\"type\":\"\",\"desc\":\"\",\"weight\":0.0}]}。"
                    + "name必须简洁稳定（同一实体多处出现用同一name）。无可抽取内容则输出空数组。"
                    + "不要输出代码块标记或任何额外说明。";
            String snippet = batchContent.length() > 2500 ? batchContent.substring(0, 2500) : batchContent;
            String resp = chat(system, snippet);
            return normalizeGraphJson(resp);
        } catch (Exception e) {
            log.warn("[openai-compat] 图谱抽取调用失败: {}", e.getMessage());
            return "{\"nodes\":[],\"edges\":[]}";
        }
    }

    /** 截取响应中第一个 JSON 对象，校验可解析后回传；失败则空图。 */
    private String normalizeGraphJson(String resp) {
        if (resp == null) {
            return "{\"nodes\":[],\"edges\":[]}";
        }
        int lb = resp.indexOf('{');
        int rb = resp.lastIndexOf('}');
        if (lb < 0 || rb < lb) {
            return "{\"nodes\":[],\"edges\":[]}";
        }
        String json = resp.substring(lb, rb + 1);
        try {
            MAPPER.readTree(json);
            return json;
        } catch (Exception e) {
            return "{\"nodes\":[],\"edges\":[]}";
        }
    }

    // ── EmbeddingProvider（暂无向量检索，返回零向量）────────────────────────

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
     * 调用 OpenAI 兼容的 /chat/completions 端点，返回回答文本。
     */
    private String chat(String system, String userContent) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            messages.add(Map.of("role", "system", "content", system));
        }
        messages.add(Map.of("role", "user", "content", userContent));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", effModel());
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);

        String reqJson = MAPPER.writeValueAsString(body);
        String bu = effBaseUrl();
        String url = bu.endsWith("/") ? bu + "chat/completions" : bu + "/chat/completions";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(effTimeout()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + effApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = MAPPER.readTree(resp.body());
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    private boolean isConfigured() {
        String k = effApiKey();
        return k != null && !k.isBlank();
    }
}
