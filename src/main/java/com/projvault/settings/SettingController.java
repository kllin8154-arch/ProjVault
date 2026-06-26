package com.projvault.settings;

import com.projvault.ai.SummaryModelProvider;
import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型配置（运行时可改）。api-key 不回显明文，仅返回是否已设置。
 */
@RestController
@RequestMapping("/api/pkc/settings")
public class SettingController {

    public static final String K_BASE_URL = "ai.base-url";
    public static final String K_API_KEY = "ai.api-key";
    public static final String K_MODEL = "ai.model";
    public static final String K_TIMEOUT = "ai.timeout";
    public static final String K_EMB_BASE_URL = "ai.embedding-base-url";
    public static final String K_EMB_API_KEY = "ai.embedding-api-key";
    public static final String K_EMB_MODEL = "ai.embedding-model";

    private final SettingService settings;
    private final SummaryModelProvider summaryProvider;

    @Value("${projvault.ai.provider:mock}")
    private String provider;

    @Value("${projvault.ai.openai-compatible.base-url:}")
    private String defBaseUrl;

    @Value("${projvault.ai.openai-compatible.model:}")
    private String defModel;

    public SettingController(SettingService settings, SummaryModelProvider summaryProvider) {
        this.settings = settings;
        this.summaryProvider = summaryProvider;
    }

    @GetMapping
    @RequirePerm("pkc:settings:manage")
    public ApiResponse<Map<String, Object>> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", provider);
        m.put("baseUrl", settings.get(K_BASE_URL, defBaseUrl));
        m.put("model", settings.get(K_MODEL, defModel));
        m.put("timeout", settings.get(K_TIMEOUT, "60"));
        m.put("apiKeySet", !settings.get(K_API_KEY, "").isBlank());
        m.put("embeddingBaseUrl", settings.get(K_EMB_BASE_URL, ""));
        m.put("embeddingModel", settings.get(K_EMB_MODEL, ""));
        m.put("embeddingApiKeySet", !settings.get(K_EMB_API_KEY, "").isBlank());
        return ApiResponse.ok(m);
    }

    @PutMapping
    @RequirePerm("pkc:settings:manage")
    public ApiResponse<Void> update(@RequestBody Map<String, String> body) {
        putIfPresent(body, "baseUrl", K_BASE_URL);
        putIfPresent(body, "model", K_MODEL);
        putIfPresent(body, "timeout", K_TIMEOUT);
        putIfPresent(body, "embeddingBaseUrl", K_EMB_BASE_URL);
        putIfPresent(body, "embeddingModel", K_EMB_MODEL);
        // api-key 仅在非空且非占位时更新
        putSecretIfPresent(body, "apiKey", K_API_KEY);
        putSecretIfPresent(body, "embeddingApiKey", K_EMB_API_KEY);
        return ApiResponse.ok();
    }

    @org.springframework.web.bind.annotation.PostMapping("/test")
    @RequirePerm("pkc:settings:manage")
    public ApiResponse<Map<String, Object>> test() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            String r = summaryProvider.summarize("连接测试", "请只回复两个字：正常", "zh");
            boolean ok = r != null && !r.startsWith("[摘要");
            m.put("ok", ok);
            m.put("message", ok ? ("连接正常：" + r.strip()) : r);
        } catch (Exception e) {
            m.put("ok", false);
            m.put("message", "连接失败：" + e.getMessage());
        }
        return ApiResponse.ok(m);
    }

    private void putIfPresent(Map<String, String> body, String field, String key) {
        if (body.containsKey(field)) {
            settings.set(key, body.get(field) == null ? "" : body.get(field).trim());
        }
    }

    private void putSecretIfPresent(Map<String, String> body, String field, String key) {
        String v = body.get(field);
        if (v != null && !v.trim().isEmpty() && !"***".equals(v.trim())) {
            settings.set(key, v.trim());
        }
    }
}
