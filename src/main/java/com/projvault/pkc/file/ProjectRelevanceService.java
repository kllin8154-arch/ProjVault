package com.projvault.pkc.file;

import com.projvault.pkc.project.Project;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProjectRelevanceService {

    public static final String IN_SCOPE = "IN_SCOPE";
    public static final String REFERENCE = "REFERENCE";
    public static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";
    public static final String SCOPE_PROJECT_CORE = "PROJECT_CORE";
    public static final String SCOPE_PROJECT_ASSUMED = "PROJECT_ASSUMED";
    public static final String SCOPE_REFERENCE_MATERIAL = "REFERENCE_MATERIAL";
    public static final String SCOPE_OTHER_PROJECT = "OTHER_PROJECT";

    private static final Pattern PROJECT_PHRASE = Pattern.compile("([\\p{IsHan}]{2,24})(项目|系统|平台)");
    private static final Set<String> GENERIC_PROJECT_WORDS = Set.of(
            "项目", "系统", "平台", "方案", "文档", "说明书", "需求", "设计", "接口",
            "建设", "管理", "服务", "智慧", "数字", "信息", "数据", "中心", "统一",
            "一期", "二期", "三期", "测试", "正式", "定稿", "初稿", "模板", "示例");
    private static final List<String> TEMPLATE_MARKERS = List.of(
            "模板", "样例", "示例", "demo", "sample", "example", "仅供参考", "参考格式");

    public RelevanceResult evaluate(Project project, FileAsset asset, String text) {
        String raw = asset.getRelPath() + "\n" + asset.getName() + "\n" + limit(text, 12000);
        String haystack = normalize(raw);
        AnchorSet anchors = anchors(project);
        double score = score(haystack, anchors);
        List<String> reasons = new ArrayList<>();

        for (String anchor : anchors.strong()) {
            if (haystack.contains(anchor)) {
                reasons.add("命中项目锚点：" + anchor);
            }
        }

        List<String> otherProjects = otherProjectPhrases(asset.getRelPath() + "\n" + asset.getName(), anchors, true);
        boolean hasTemplatePath = containsAny(normalize(asset.getRelPath() + "\n" + asset.getName()), TEMPLATE_MARKERS);

        if (hasTemplatePath && score < 3.0) {
            return new RelevanceResult(REFERENCE, Math.max(score, 0.35),
                    "路径或文件名显示为模板/示例/样例资料",
                    SCOPE_REFERENCE_MATERIAL,
                    "作为参考资料保留，可用于人工查看，但默认不参与配置提取、图谱构建和问答证据");
        }
        if (!otherProjects.isEmpty()) {
            return new RelevanceResult(OUT_OF_SCOPE, Math.min(score, 0.2),
                    "疑似其他项目资料：" + String.join("、", otherProjects.subList(0, Math.min(3, otherProjects.size()))),
                    SCOPE_OTHER_PROJECT,
                    "命中了其他项目/系统/平台名称，且未与当前项目画像形成包含或同名关系");
        }
        if (score >= 1.0) {
            return new RelevanceResult(IN_SCOPE, Math.min(1.0, score / 6.0),
                    reasons.isEmpty() ? "内容与当前项目画像匹配" : String.join("；", reasons.subList(0, Math.min(3, reasons.size()))),
                    SCOPE_PROJECT_CORE,
                    "命中当前项目名称、项目代号或根目录派生锚点，可作为本项目核心资料");
        }
        if (!otherProjects.isEmpty()) {
            return new RelevanceResult(OUT_OF_SCOPE, Math.min(score, 0.3),
                    "疑似其他项目资料：" + String.join("、", otherProjects.subList(0, Math.min(3, otherProjects.size()))),
                    SCOPE_OTHER_PROJECT,
                    "命中了其他项目/系统/平台名称，且未与当前项目画像形成包含或同名关系");
        }
        if (hasTemplatePath) {
            return new RelevanceResult(REFERENCE, Math.max(score, 0.3),
                    "路径或文件名显示为模板/示例/样例资料",
                    SCOPE_REFERENCE_MATERIAL,
                    "作为参考资料保留，可用于人工查看，但默认不参与配置提取、图谱构建和问答证据");
        }
        return new RelevanceResult(IN_SCOPE, 0.45,
                "未发现其他项目或模板特征，按项目资料纳入",
                SCOPE_PROJECT_ASSUMED,
                "没有明确排除信号，先纳入项目资料；建议后续通过人工确认或更多证据提升置信度");
    }

    public static boolean isKnowledgeEligible(FileAsset file) {
        String status = file == null ? null : file.getRelevanceStatus();
        return status == null || status.isBlank() || IN_SCOPE.equals(status);
    }

    public static boolean isOutOfScope(FileAsset file) {
        return file != null && OUT_OF_SCOPE.equals(file.getRelevanceStatus());
    }

    public static boolean isReference(FileAsset file) {
        return file != null && REFERENCE.equals(file.getRelevanceStatus());
    }

    private double score(String haystack, AnchorSet anchors) {
        double score = 0.0;
        for (String anchor : anchors.strong()) {
            if (anchor.length() >= 3 && haystack.contains(anchor)) {
                score += anchor.length() >= 5 ? 2.0 : 1.0;
            }
        }
        for (String anchor : anchors.weak()) {
            if (anchor.length() >= 3 && haystack.contains(anchor)) {
                score += 0.45;
            }
        }
        return score;
    }

    private List<String> otherProjectPhrases(String raw, AnchorSet anchors, boolean includeSystemsAndPlatforms) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher matcher = PROJECT_PHRASE.matcher(limit(raw == null ? "" : raw, 5000));
        while (matcher.find()) {
            String phrase = normalize(matcher.group());
            String suffix = normalize(matcher.group(2));
            if (!includeSystemsAndPlatforms && !"项目".equals(suffix)) {
                continue;
            }
            if (phrase.length() < 5 || isCompatibleProjectPhrase(phrase, anchors)) {
                continue;
            }
            if (isGenericPhrase(phrase)) {
                continue;
            }
            out.add(phrase);
        }
        return new ArrayList<>(out);
    }

    private boolean isCompatibleProjectPhrase(String phrase, AnchorSet anchors) {
        for (String anchor : anchors.strong()) {
            if (anchor.length() >= 3 && (phrase.contains(anchor) || anchor.contains(phrase))) {
                return true;
            }
        }
        for (String anchor : anchors.weak()) {
            if (anchor.length() >= 4 && (phrase.contains(anchor) || anchor.contains(phrase))) {
                return true;
            }
        }
        return false;
    }

    private boolean isGenericPhrase(String phrase) {
        if (phrase.contains("本项目") || phrase.contains("该项目") || phrase.contains("此项目")
                || phrase.contains("本系统") || phrase.contains("该系统") || phrase.contains("此系统")
                || phrase.contains("本平台") || phrase.contains("该平台") || phrase.contains("此平台")) {
            return true;
        }
        String compact = phrase.replace("项目", "").replace("系统", "").replace("平台", "");
        return compact.length() <= 2 || GENERIC_PROJECT_WORDS.contains(compact);
    }

    private AnchorSet anchors(Project project) {
        LinkedHashSet<String> strong = new LinkedHashSet<>();
        LinkedHashSet<String> weak = new LinkedHashSet<>();
        addAnchor(strong, normalize(project == null ? null : project.getName()));
        addAnchor(weak, normalize(project == null ? null : project.getCode()));

        String rootLeaf = "";
        if (project != null && project.getRootPath() != null) {
            rootLeaf = normalize(lastPathSegment(project.getRootPath()).replaceFirst("^\\d+", ""));
            addAnchor(strong, rootLeaf);
        }
        addDerivedAnchors(normalize(project == null ? null : project.getName()), strong, weak);
        addDerivedAnchors(rootLeaf, strong, weak);
        return new AnchorSet(strong, weak);
    }

    private void addDerivedAnchors(String text, Set<String> strong, Set<String> weak) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = PROJECT_PHRASE.matcher(text);
        while (matcher.find()) {
            String phrase = normalize(matcher.group());
            addAnchor(strong, phrase);
            addAnchor(strong, stripProjectSuffix(phrase));
        }
        for (String segment : splitProjectText(text)) {
            addAnchor(strong, segment);
            addUsefulNgrams(segment, strong, weak);
        }
    }

    private void addUsefulNgrams(String text, Set<String> strong, Set<String> weak) {
        if (text == null || text.length() < 3) {
            return;
        }
        int maxLen = Math.min(8, text.length());
        for (int len = maxLen; len >= 4; len--) {
            String token = text.substring(text.length() - len);
            if (isUsefulAnchor(token)) {
                strong.add(token);
            }
        }
        String shortTail = text.substring(text.length() - 3);
        if (isUsefulAnchor(shortTail)) {
            weak.add(shortTail);
        }
    }

    private void addAnchor(Set<String> anchors, String value) {
        if (!isUsefulAnchor(value)) {
            return;
        }
        anchors.add(value);
    }

    private boolean isUsefulAnchor(String value) {
        if (value == null || value.length() < 3) {
            return false;
        }
        if (GENERIC_PROJECT_WORDS.contains(value)) {
            return false;
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (value.endsWith("项目") || value.endsWith("系统") || value.endsWith("平台")) {
            return value.length() >= 5 && !GENERIC_PROJECT_WORDS.contains(stripProjectSuffix(value));
        }
        return true;
    }

    private List<String> splitProjectText(String text) {
        List<String> segments = new ArrayList<>();
        for (String raw : text.split("(项目|系统|平台|中心|目录|资料|文档|阶段|相关|材料)+")) {
            String segment = normalize(raw);
            if (segment.length() >= 3) {
                segments.add(segment);
            }
        }
        if (text.length() >= 3) {
            segments.add(text);
        }
        return segments;
    }

    private String stripProjectSuffix(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(项目|系统|平台)$", "");
    }

    private String lastPathSegment(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static boolean containsAny(String haystack, List<String> markers) {
        for (String marker : markers) {
            if (haystack.contains(normalize(marker))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String limit(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private record AnchorSet(Set<String> strong, Set<String> weak) {}

    public record RelevanceResult(String status, double score, String reason,
                                  String scopeType, String scopeReason) {}
}
