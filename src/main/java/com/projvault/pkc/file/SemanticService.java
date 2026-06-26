package com.projvault.pkc.file;

import com.projvault.ai.SummaryModelProvider;
import com.projvault.pkc.project.Project;
import com.projvault.pkc.scan.ScanCancellation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates file summaries and lightweight content-based classification.
 */
@Service
public class SemanticService {

    private static final Logger log = LoggerFactory.getLogger(SemanticService.class);
    private static final int MAX_CONTENT_CHARS = 4000;

    private final SummaryModelProvider summaryModelProvider;
    private final FileAssetRepository fileAssetRepository;
    private final ScanCancellation scanCancellation;
    private final ProjectRelevanceService projectRelevanceService;

    @Value("${projvault.ai.language:zh}")
    private String language;

    public SemanticService(SummaryModelProvider summaryModelProvider,
                           FileAssetRepository fileAssetRepository,
                           ScanCancellation scanCancellation,
                           ProjectRelevanceService projectRelevanceService) {
        this.summaryModelProvider = summaryModelProvider;
        this.fileAssetRepository = fileAssetRepository;
        this.scanCancellation = scanCancellation;
        this.projectRelevanceService = projectRelevanceService;
    }

    @Transactional
    public int analyze(List<ParseResult> results, Long scanId, Project project) {
        int count = 0;
        for (ParseResult r : results) {
            if (scanCancellation.isCancelled(scanId)) {
                log.info("[semantic] cancelled, processed={}", count);
                break;
            }
            FileAsset asset = fileAssetRepository.findById(r.fileId()).orElse(null);
            if (asset == null) {
                log.warn("[semantic] fileId={} not found, skipped", r.fileId());
                continue;
            }
            if (!r.parseOk() || r.fullText() == null) {
                applyPathOnlyRelevance(project, asset);
                count++;
                continue;
            }
            try {
                String text = truncate(r.fullText(), MAX_CONTENT_CHARS);
                String summary = summaryModelProvider.summarize(asset.getRelPath(), text, language);
                asset.setSummary(summary);
                asset.setDocType(inferDocType(asset, text));
                asset.setTags(inferTags(asset, text, summary));
                ProjectRelevanceService.RelevanceResult relevance =
                        projectRelevanceService.evaluate(project, asset, r.fullText());
                asset.setRelevanceStatus(relevance.status());
                asset.setRelevanceScore(relevance.score());
                asset.setRelevanceReason(relevance.reason());
                asset.setScopeType(relevance.scopeType());
                asset.setScopeReason(relevance.scopeReason());
                asset.setUpdatedAt(LocalDateTime.now());
                fileAssetRepository.save(asset);
                count++;
            } catch (Exception e) {
                log.warn("[semantic] failed fileId={}: {}", r.fileId(), e.getMessage());
            }
        }
        log.info("[semantic] completed, processed={}", count);
        return count;
    }

    @Transactional
    public int fillMissingRelevance(Project project) {
        int count = 0;
        for (FileAsset asset : fileAssetRepository.findByProjectIdAndDeletedFlagFalse(project.getId())) {
            boolean hasStatus = asset.getRelevanceStatus() != null && !asset.getRelevanceStatus().isBlank();
            boolean hasScope = asset.getScopeType() != null && !asset.getScopeType().isBlank();
            if (hasStatus && hasScope) {
                continue;
            }
            applyPathOnlyRelevance(project, asset);
            count++;
        }
        return count;
    }

    private void applyPathOnlyRelevance(Project project, FileAsset asset) {
        ProjectRelevanceService.RelevanceResult relevance =
                projectRelevanceService.evaluate(project, asset, "");
        asset.setRelevanceStatus(relevance.status());
        asset.setRelevanceScore(relevance.score());
        asset.setRelevanceReason(relevance.reason());
        asset.setScopeType(relevance.scopeType());
        asset.setScopeReason(relevance.scopeReason());
        asset.setUpdatedAt(LocalDateTime.now());
        fileAssetRepository.save(asset);
    }

    private String inferDocType(FileAsset asset, String text) {
        String haystack = normalize(asset.getRelPath() + "\n" + text);
        String ext = asset.getExt() == null ? "" : asset.getExt().toLowerCase();

        if (containsAny(haystack, "\\u4ed8\\u6b3e", "\\u8282\\u70b9", "\\u6bd4\\u4f8b", "\\u8d23\\u4efb\\u90e8\\u95e8")) {
            return decode("\\u53f0\\u8d26\\u6e05\\u5355");
        }
        if (containsAny(haystack, "\\u4f1a\\u8bae\\u7eaa\\u8981", "\\u53c2\\u4f1a\\u4eba", "\\u4f1a\\u8bae\\u7ed3\\u8bba")) {
            return decode("\\u4f1a\\u8bae\\u7eaa\\u8981");
        }
        if (containsAny(haystack, "\\u5408\\u540c", "\\u8865\\u5145\\u534f\\u8bae", "\\u5408\\u540c\\u5916\\u53d8\\u66f4")) {
            return decode("\\u5408\\u540c\\u53d8\\u66f4");
        }
        if (containsAny(haystack, "\\u539f\\u578b", "prototype", "\\u4ea4\\u4e92", "\\u5de5\\u4f5c\\u53f0")) {
            return decode("\\u539f\\u578b\\u8bf4\\u660e");
        }
        if (containsAny(haystack, "\\u9a8c\\u6536", "acceptance", "\\u53ef\\u4fe1\\u5ea6", "\\u95ee\\u9898\\u6e05\\u5355")) {
            return decode("\\u9a8c\\u6536\\u6587\\u6863");
        }
        if (containsAny(haystack, "\\u9700\\u6c42", "requirement", "\\u5fc5\\u987b\\u80fd\\u56de\\u7b54")) {
            return decode("\\u9700\\u6c42\\u6587\\u6863");
        }
        if (containsAny(haystack, "\\u63a5\\u53e3", "api", "\\u8fb9\\u754c")) {
            return decode("\\u63a5\\u53e3\\u6587\\u6863");
        }
        if (containsAny(haystack, "\\u6280\\u672f\\u65b9\\u6848", "\\u67b6\\u6784", "design")) {
            return decode("\\u6280\\u672f\\u65b9\\u6848");
        }
        if (containsAny(haystack, "\\u90e8\\u7f72", "deploy", "install")) {
            return decode("\\u90e8\\u7f72\\u624b\\u518c");
        }
        if (isConfigExt(ext)) {
            return decode("\\u914d\\u7f6e\\u6587\\u4ef6");
        }
        if (ext.equals("md") || ext.equals("txt") || ext.equals("html") || ext.equals("htm")) {
            return decode("\\u8bf4\\u660e\\u6587\\u6863");
        }
        return decode("\\u5176\\u4ed6");
    }

    private String inferTags(FileAsset asset, String text, String summary) {
        String haystack = normalize(asset.getRelPath() + "\n" + text + "\n" + summary);
        Set<String> tags = new LinkedHashSet<>();

        addIf(tags, haystack, "\\u5408\\u540c", "\\u5408\\u540c", "\\u8865\\u5145\\u534f\\u8bae");
        addIf(tags, haystack, "\\u53d8\\u66f4", "\\u53d8\\u66f4", "cr-");
        addIf(tags, haystack, "\\u539f\\u578b", "\\u539f\\u578b", "prototype", "\\u4ea4\\u4e92");
        addIf(tags, haystack, "\\u4ed8\\u6b3e", "\\u4ed8\\u6b3e", "\\u6bd4\\u4f8b");
        addIf(tags, haystack, "\\u9a8c\\u6536", "\\u9a8c\\u6536", "acceptance");
        addIf(tags, haystack, "\\u98ce\\u9669", "\\u98ce\\u9669", "\\u65e0\\u5408\\u540c\\u4f9d\\u636e");
        addIf(tags, haystack, "\\u63a5\\u53e3", "\\u63a5\\u53e3", "api", "\\u8fb9\\u754c");
        addIf(tags, haystack, "\\u4eba\\u5458", "\\u8d75\\u5c9a", "\\u5468\\u542f\\u660e", "\\u6797\\u6c90", "\\u97e9\\u4ea6");
        addIf(tags, haystack, "AI", "ai", "\\u65e0\\u8d44\\u6599\\u4f9d\\u636e", "\\u8bc1\\u636e");

        return tags.isEmpty() ? null : String.join(",", tags);
    }

    private static void addIf(Set<String> tags, String haystack, String tag, String... needles) {
        if (containsAny(haystack, needles)) {
            tags.add(decode(tag));
        }
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String raw : needles) {
            String needle = decode(raw).toLowerCase();
            if (!needle.isEmpty() && haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static String decode(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i + 5 < s.length() && s.charAt(i) == '\\' && s.charAt(i + 1) == 'u') {
                String hex = s.substring(i + 2, i + 6);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {
                    // Fall through and append the original char.
                }
            }
            out.append(s.charAt(i));
        }
        return out.toString();
    }

    private boolean isConfigExt(String ext) {
        return ext.equals("yml") || ext.equals("yaml") || ext.equals("properties")
                || ext.equals("conf") || ext.equals("ini") || ext.equals("toml")
                || ext.equals("json") || ext.equals("xml") || ext.equals("env");
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
