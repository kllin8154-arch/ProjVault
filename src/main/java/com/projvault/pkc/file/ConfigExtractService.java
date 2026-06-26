package com.projvault.pkc.file;

import com.projvault.ai.ExtractModelProvider;
import com.projvault.ai.ExtractedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P5 配置提取服务（v0.4 去噪版）。
 *
 * 去噪策略：
 * 1. category 白名单：只处理 doc / config 类文件，跳过源码（other/archive/image）
 * 2. HOST_PORT 值黑名单：过滤通用占位词（TRUE/FALSE/port/hostname 等）
 * 3. 示例值过滤：值中含 "..." "xxx" 等模板占位符的直接丢弃
 */
@Service
public class ConfigExtractService {

    private static final Logger log = LoggerFactory.getLogger(ConfigExtractService.class);

    /** 只对这些 category 的文件做配置提取 */
    private static final Set<String> EXTRACT_CATEGORY = Set.of(
            FileCategoryResolver.DOC, FileCategoryResolver.CONFIG);

    /** HOST_PORT 值黑名单（全部小写匹配） */
    private static final Set<String> HOST_BLACKLIST = Set.of(
            "true", "false", "null", "none", "localhost",
            "port", "host", "hostname", "server", "addr", "address",
            "value", "name", "username", "password", "endpoint",
            "broker", "url", "path", "key", "token");

    /** 值中包含这些子串时视为模板/注释，直接过滤 */
    private static final List<String> TEMPLATE_MARKERS = List.of(
            "...", "xxx", "<", ">", "{", "}", "[", "]",
            "your-", "example", "<host", "<port");

    private static final List<String> EXAMPLE_CONTEXT_MARKERS = List.of(
            "示例", "样例", "例子", "例如", "如：", "如:", "举例", "模板", "占位", "演示",
            "仅供参考", "参考格式", "填写格式", "格式如下", "api 示例", "json 示例",
            "example", "sample", "demo", "placeholder", "for example");

    private static final List<String> REAL_CONTEXT_MARKERS = List.of(
            "生产", "正式", "实际", "部署", "连接", "接入", "服务器地址",
            "数据库地址", "服务地址", "内网地址", "外网地址");

    /** URL 类型：文档/工具仓库域名，非真实服务地址（pom.xml 注释中常见） */
    private static final List<String> URL_DOC_DOMAINS = List.of(
            "repo.maven.apache.org", "maven.apache.org", "docs.spring.io",
            "plugins.gradle.org", "schema.org", "www.w3.org",
            "xml.apache.org", "github.com/spring", "search.maven.org",
            // 公共参考/百科/搜索域名：文档里的引用链接，非项目服务地址
            "baidu.com", "google.com", "wikipedia.org", "zhihu.com",
            "csdn.net", "cnblogs.com", "stackoverflow.com", "microsoft.com",
            "qq.com", "163.com", "sina.com", "aliyun.com", "tencent.com",
            "jianshu.com", "juejin.cn", "gitee.com",
            "localhost", "127.0.0.1", "qlogo.cn", "kdocs.cn", "doc.weixin.qq.com");

    // ── 正则规则 ─────────────────────────────────────────────────────────────

    private static final Pattern JDBC_URL = Pattern.compile(
            "jdbc:[a-zA-Z0-9+]+://[^\\s\"'<>\\[\\]]{5,300}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HTTP_URL = Pattern.compile(
            "https?://[^\\s\"'<>\\[\\]()\\u3000-\\u303f\\uff00-\\uffef\\u4e00-\\u9fff]{5,200}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REDIS_URL = Pattern.compile(
            "redis://[^\\s\"'<>\\[\\]]{3,200}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MQ_URL = Pattern.compile(
            "amqps?://[^\\s\"'<>\\[\\]]{3,200}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IP_PORT = Pattern.compile(
            "\\b(?!127\\.0\\.0\\.1|0\\.0\\.0\\.0)(\\d{1,3}\\.){3}\\d{1,3}(:\\d{2,5})?\\b");

    private static final Pattern HOST_KV = Pattern.compile(
            "(?i)(?:host|server|addr(?:ess)?|broker|endpoint)\\s*[=:]\\s*([a-zA-Z0-9._-]{3,100}(?::\\d{2,5})?)");

    // ── 提取入口 ─────────────────────────────────────────────────────────────

    private final ExtractModelProvider extractModelProvider;
    private final com.projvault.pkc.scan.ScanCancellation scanCancellation;

    public ConfigExtractService(ExtractModelProvider extractModelProvider,
                                com.projvault.pkc.scan.ScanCancellation scanCancellation) {
        this.extractModelProvider = extractModelProvider;
        this.scanCancellation = scanCancellation;
    }

    @Transactional
    public int extract(List<ParseResult> results, Long projectId, Long scanId,
                       FileAssetRepository fileAssetRepository,
                       ConfigItemRepository configItemRepository) {
        int total = 0;
        for (ParseResult r : results) {
            if (scanCancellation.isCancelled(scanId)) {
                log.info("[extract] 收到取消，停止配置提取（已处理 {} 条）", total);
                break;
            }
            if (!r.parseOk() || r.fullText() == null) {
                configItemRepository.deleteByFileId(r.fileId());
                continue;
            }
            FileAsset asset = fileAssetRepository.findById(r.fileId()).orElse(null);
            if (asset == null) {
                continue;
            }
            if (!shouldExtract(asset, r.fullText())) {
                log.debug("[extract] 跳过低相关文件（status={}）: {}",
                        asset.getRelevanceStatus(), asset.getRelPath());
                configItemRepository.deleteByFileId(asset.getId());
                continue;
            }
            // category 白名单过滤（跳过源码/归档/图片）
            String cat = asset.getCategory();
            if (cat == null || !EXTRACT_CATEGORY.contains(cat)) {
                log.debug("[extract] 跳过（category={}）: {}", cat, asset.getRelPath());
                continue;
            }
            // 保留上次人工确认/驳回的决定（按 keyType+keyValue），重扫不丢失
            java.util.Map<String, String> prevDecision = new java.util.HashMap<>();
            for (ConfigItem p : configItemRepository.findByFileId(asset.getId())) {
                if (!"PENDING".equals(p.getReviewStatus())) {
                    prevDecision.put(p.getKeyType() + "|" + p.getKeyValue(), p.getReviewStatus());
                }
            }
            configItemRepository.deleteByFileId(asset.getId());
            List<ConfigItem> candidates = extractFromText(
                    r.fullText(), projectId, asset.getId(), scanId);
            List<ConfigItem> decided = new ArrayList<>();
            List<ConfigItem> fresh = new ArrayList<>();
            for (ConfigItem ci : candidates) {
                String st = prevDecision.get(ci.getKeyType() + "|" + ci.getKeyValue());
                if (st != null) {
                    ci.setReviewStatus(st);
                    ci.setReviewedAt(java.time.LocalDateTime.now());
                    decided.add(ci);
                } else {
                    fresh.add(ci);
                }
            }
            fresh = refineWithLlm(asset.getRelPath(), fresh);
            List<ConfigItem> items = new ArrayList<>(decided);
            items.addAll(fresh);
            configItemRepository.saveAll(items);
            total += items.size();
            if (!items.isEmpty()) {
                log.debug("[extract] {} -> {} 条配置项", asset.getRelPath(), items.size());
            }
        }
        log.info("[extract] P5 完成，共提取 {} 条配置项", total);
        return total;
    }

    // ── 单文本提取 ───────────────────────────────────────────────────────────

    List<ConfigItem> extractFromText(String text, Long projectId, Long fileId, Long scanId) {
        List<ConfigItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        extract(items, seen, text, JDBC_URL,  "JDBC_URL",  projectId, fileId, scanId);
        extract(items, seen, text, HTTP_URL,  "URL",       projectId, fileId, scanId);
        extract(items, seen, text, REDIS_URL, "REDIS_URL", projectId, fileId, scanId);
        extract(items, seen, text, MQ_URL,    "MQ_URL",    projectId, fileId, scanId);
        extract(items, seen, text, IP_PORT,   "IP_PORT",   projectId, fileId, scanId);
        extractGroup(items, seen, text, HOST_KV, 1, "HOST_PORT", projectId, fileId, scanId);
        return items;
    }

    boolean shouldExtract(FileAsset asset, String text) {
        if (asset == null || ProjectRelevanceService.isOutOfScope(asset)) {
            return false;
        }
        return ProjectRelevanceService.isKnowledgeEligible(asset)
                || (ProjectRelevanceService.isReference(asset) && isRealContext(text));
    }

    private void extract(List<ConfigItem> items, Set<String> seen,
                         String text, Pattern p, String keyType,
                         Long projectId, Long fileId, Long scanId) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            String val = stripTrailingPunct(m.group().strip());
            String ctx = context(text, m.start(), m.end());
            if (isNoise(val, keyType) || isExampleContext(ctx, val)
                    || isExampleCodeBlock(text, m.start(), ctx) || !seen.add(keyType + "|" + val)) {
                continue;
            }
            items.add(buildItem(projectId, fileId, scanId, keyType, val, ctx));
        }
    }

    private void extractGroup(List<ConfigItem> items, Set<String> seen,
                              String text, Pattern p, int group, String keyType,
                              Long projectId, Long fileId, Long scanId) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            String val = stripTrailingPunct(m.group(group).strip());
            String ctx = context(text, m.start(), m.end());
            if (isNoise(val, keyType) || isExampleContext(ctx, val)
                    || isExampleCodeBlock(text, m.start(), ctx) || !seen.add(keyType + "|" + val)) {
                continue;
            }
            items.add(buildItem(projectId, fileId, scanId, keyType, val, ctx));
        }
    }

    // ── §9 第二级：LLM 精配过滤 ───────────────────────────────────────────────

    /**
     * 用 LLM 逐段判断正则候选是否为真实配置；命中则保留。
     * 防御：模型整体无产出（未配置/调用失败）时回退保留全部正则结果，避免误删。
     */
    private List<ConfigItem> refineWithLlm(String docName, List<ConfigItem> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        Map<String, List<ConfigItem>> byCtx = new LinkedHashMap<>();
        for (ConfigItem ci : candidates) {
            byCtx.computeIfAbsent(ci.getContext() == null ? "" : ci.getContext(),
                    k -> new ArrayList<>()).add(ci);
        }
        List<ConfigItem> kept = new ArrayList<>();
        int llmTotal = 0;
        for (Map.Entry<String, List<ConfigItem>> e : byCtx.entrySet()) {
            List<ExtractedItem> llm;
            try {
                llm = extractModelProvider.extract(docName, e.getKey());
            } catch (Exception ex) {
                llm = List.of();
            }
            llmTotal += llm.size();
            Set<String> vals = new LinkedHashSet<>();
            for (ExtractedItem it : llm) {
                if (it.value() != null && !it.value().isBlank()) {
                    vals.add(it.value().toLowerCase().strip());
                }
            }
            for (ConfigItem ci : e.getValue()) {
                if (isRealContext(ci.getContext())) {
                    kept.add(ci);
                    continue;
                }
                String v = ci.getKeyValue() == null ? "" : ci.getKeyValue().toLowerCase().strip();
                boolean ok = false;
                for (String lv : vals) {
                    if (lv.contains(v) || v.contains(lv)) {
                        ok = true;
                        break;
                    }
                }
                if (ok) {
                    kept.add(ci);
                }
            }
        }
        if (llmTotal == 0) {
            return candidates;
        }
        log.info("[extract] LLM 精配 {} — {} → {} 条", docName, candidates.size(), kept.size());
        return kept;
    }

    // ── 噪音判断 ─────────────────────────────────────────────────────────────

    private boolean isNoise(String val, String keyType) {
        if (val == null || val.length() < 3) {
            return true;
        }
        // 模板占位符过滤（含 ... xxx < > { }）
        String lower = val.toLowerCase();
        for (String marker : TEMPLATE_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        // 文档/工具类 URL 过滤（pom.xml 注释链接不是真实服务地址）
        if ("URL".equals(keyType)) {
            for (String domain : URL_DOC_DOMAINS) {
                if (lower.contains(domain)) {
                    return true;
                }
            }
        }
        // HOST_PORT 黑名单
        if ("HOST_PORT".equals(keyType)) {
            // 去掉端口部分再判断
            String host = lower.contains(":") ? lower.substring(0, lower.indexOf(':')) : lower;
            if (HOST_BLACKLIST.contains(host)) {
                return true;
            }
            // 纯数字（端口号被误当主机名）
            if (host.matches("\\d+")) {
                return true;
            }
        }
        // IP_PORT：过滤文档层级编号（如 1.1.1.1 / 3.4.1.2），仅保留带端口或私网/回环 IP
        if ("IP_PORT".equals(keyType) && !isPlausibleIp(val)) {
            return true;
        }
        return false;
    }

    private boolean isExampleContext(String context, String value) {
        if (context == null || context.isBlank()) {
            return false;
        }
        String lower = context.toLowerCase();
        String v = value == null ? "" : value.toLowerCase();
        boolean explicitExample = hasAny(lower, EXAMPLE_CONTEXT_MARKERS);
        boolean realContext = isRealContext(context);
        if (explicitExample && !realContext) {
            return true;
        }
        if (realContext) {
            return false;
        }
        if ((v.startsWith("192.168.") || v.startsWith("10.") || v.startsWith("172."))
                && (lower.contains("请替换") || lower.contains("自行填写") || lower.contains("按需修改"))) {
            return true;
        }
        return false;
    }

    private boolean isExampleCodeBlock(String text, int position, String context) {
        if (text == null || position < 0 || position > text.length()) {
            return false;
        }
        int blockStart = text.lastIndexOf("```", position);
        if (blockStart < 0) {
            return false;
        }
        if (!isInsideFencedBlock(text, position)) {
            return false;
        }
        String before = text.substring(Math.max(0, blockStart - 160), blockStart).toLowerCase();
        String ctx = context == null ? "" : context.toLowerCase();
        return before.contains("api")
                || before.contains("example")
                || before.contains("sample")
                || before.contains("demo")
                || ctx.contains("example-host")
                || ctx.contains("/mock/")
                || ctx.contains("/demo");
    }

    private boolean isInsideFencedBlock(String text, int position) {
        int count = 0;
        int cursor = 0;
        while (cursor >= 0 && cursor < position) {
            int next = text.indexOf("```", cursor);
            if (next < 0 || next >= position) {
                break;
            }
            count++;
            cursor = next + 3;
        }
        return count % 2 == 1;
    }

    private boolean isRealContext(String context) {
        if (context == null || context.isBlank()) {
            return false;
        }
        return hasAny(context.toLowerCase(), REAL_CONTEXT_MARKERS);
    }

    private static boolean hasAny(String text, List<String> markers) {
        for (String marker : markers) {
            if (text.contains(marker.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** 是否像真实 IP（带端口，或私网/回环段）；否则视为文档层级编号噪声。 */
    private static boolean isPlausibleIp(String val) {
        boolean hasPort = val.contains(":");
        String ip = hasPort ? val.substring(0, val.indexOf(':')) : val;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] o = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                o[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (o[i] < 0 || o[i] > 255) {
                return false;
            }
        }
        if (hasPort) {
            return true;
        }
        if (o[0] == 10) {
            return true;
        }
        if (o[0] == 172 && o[1] >= 16 && o[1] <= 31) {
            return true;
        }
        if (o[0] == 192 && o[1] == 168) {
            return true;
        }
        return o[0] == 127;
    }

    private ConfigItem buildItem(Long projectId, Long fileId, Long scanId,
                                  String keyType, String keyValue, String context) {
        ConfigItem ci = new ConfigItem();
        ci.setProjectId(projectId);
        ci.setFileId(fileId);
        ci.setScanId(scanId);
        ci.setKeyType(keyType);
        ci.setKeyValue(keyValue.length() > 1024 ? keyValue.substring(0, 1024) : keyValue);
        ci.setContext(context);
        return ci;
    }

    /** 去除结尾的 Markdown/Shell 标点（反引号、反斜线、右括号、分号等） */
    private static String stripTrailingPunct(String val) {
        return val.replaceAll("[`'\"\\\\,);|]+$", "");
    }

    private String context(String text, int start, int end) {
        int from = Math.max(0, start - 80);
        int to   = Math.min(text.length(), end + 80);
        int headingStart = Math.max(
                Math.max(text.lastIndexOf("\n# ", start), text.lastIndexOf("\n## ", start)),
                text.lastIndexOf("\n### ", start));
        if (headingStart >= from) {
            from = headingStart + 1;
        }
        int nextHeading = firstPositive(
                text.indexOf("\n# ", end),
                text.indexOf("\n## ", end),
                text.indexOf("\n### ", end));
        if (nextHeading >= 0 && nextHeading < to) {
            to = nextHeading;
        }
        String ctx = text.substring(from, to).replace("\n", " ").strip();
        return ctx.length() > 512 ? ctx.substring(0, 512) : ctx;
    }

    private int firstPositive(int... values) {
        int result = -1;
        for (int value : values) {
            if (value >= 0 && (result < 0 || value < result)) {
                result = value;
            }
        }
        return result;
    }
}
