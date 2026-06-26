package com.projvault.pkc.artifact;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArtifactQualityService {

    private static final Pattern PENDING = Pattern.compile("(?i)待确认|待补充|TODO|TBD|未确认");
    private static final List<Pattern> FACT_PATTERNS = List.of(
            Pattern.compile("\\b20\\d{2}[-/.年](?:0?[1-9]|1[0-2])[-/.月](?:0?[1-9]|[12]\\d|3[01])日?\\b"),
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{2,5})?\\b"),
            Pattern.compile("https?://[^\\s)>,，。]+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<![A-Za-z0-9_])\\d[\\d,]*(?:\\.\\d+)?\\s*(?:万元|元|人民币)(?![A-Za-z0-9_])"),
            Pattern.compile("\\b[A-Z]{2,}(?:-[A-Z0-9]+)+\\b"));
    private static final List<Pattern> STRONG_ASSERTION_PATTERNS = List.of(
            Pattern.compile("证明[^，。；;\\n]{1,60}"),
            Pattern.compile("不存在[^，。；;\\n]{1,60}"),
            Pattern.compile("唯一[^，。；;\\n]{1,60}"),
            Pattern.compile("(?:已经|已)(?:确认|存档|生效|清除|完成)[^，。；;\\n]{0,60}"),
            Pattern.compile("保持恒定[^，。；;\\n]{0,60}"));

    public ArtifactQualityDTO evaluate(String title,
                                       String content,
                                       String instructions,
                                       List<ArtifactEvidenceDTO> evidence,
                                       List<String> formatChecks) {
        int completeness = completenessScore(content);
        boolean encodingDamaged = looksLikeMojibake(content);
        int pendingCount = countPendingItems(content);
        Set<String> facts = extractFacts(content);
        String supportCorpus = normalize(title + "\n" + safe(instructions) + "\n"
                + evidence.stream().map(ArtifactEvidenceDTO::excerpt).reduce("", (a, b) -> a + "\n" + safe(b)));
        LinkedHashSet<String> unsupportedItems = new LinkedHashSet<>(facts.stream()
                .filter(fact -> !supportCorpus.contains(normalize(fact)))
                .toList());
        extractStrongAssertions(content).stream()
                .filter(claim -> !supportCorpus.contains(normalize(claim)))
                .forEach(unsupportedItems::add);
        List<String> unsupported = unsupportedItems.stream().limit(12).toList();
        int checkedClaims = facts.size() + extractStrongAssertions(content).size();
        int coverage = evidenceCoverage(evidence, checkedClaims, unsupported.size());

        List<String> warnings = new ArrayList<>();
        if (pendingCount > 0) {
            warnings.add("正文包含 " + pendingCount + " 个待确认或待补充项");
        }
        if (!unsupported.isEmpty()) {
            warnings.add("发现 " + unsupported.size() + " 个未在证据或修改要求中直接出现的高风险事实");
        }
        if (evidence.isEmpty()) {
            warnings.add("没有精确到原文分块的证据，仅可作为历史草稿查看");
        }
        if (encodingDamaged) {
            warnings.add("正文疑似发生字符编码损坏，请从上一有效版本重新编辑");
        }

        String status;
        if (completeness < 60 || formatChecks.isEmpty() || encodingDamaged) {
            status = "FAILED";
        } else if (!warnings.isEmpty()) {
            status = "WARNING";
        } else {
            status = "PASSED";
        }
        return new ArtifactQualityDTO(status, completeness, coverage, pendingCount,
                unsupported, List.copyOf(formatChecks), List.copyOf(warnings));
    }

    private int completenessScore(String content) {
        String text = safe(content).strip();
        if (text.isEmpty()) {
            return 0;
        }
        int score = 100;
        if (text.length() < 120) {
            score -= 25;
        }
        if (text.endsWith("...") || text.endsWith("……") || text.endsWith("，") || text.endsWith(",")) {
            score -= 30;
        }
        long fences = text.lines().filter(line -> line.strip().startsWith("```")).count();
        if (fences % 2 != 0) {
            score -= 40;
        }
        return Math.max(0, score);
    }

    private int evidenceCoverage(List<ArtifactEvidenceDTO> evidence, int factCount, int unsupportedCount) {
        if (evidence.isEmpty()) {
            return 0;
        }
        if (factCount == 0) {
            return evidence.size() >= 2 ? 100 : 80;
        }
        return Math.max(0, (int) Math.round(100.0 * (factCount - unsupportedCount) / factCount));
    }

    private Set<String> extractFacts(String content) {
        Set<String> facts = new LinkedHashSet<>();
        for (Pattern pattern : FACT_PATTERNS) {
            Matcher matcher = pattern.matcher(safe(content));
            while (matcher.find() && facts.size() < 40) {
                facts.add(matcher.group().strip());
            }
        }
        return facts;
    }

    private int countPendingItems(String content) {
        int count = 0;
        for (String line : safe(content).split("\\R")) {
            String plain = line.strip().replaceFirst("^[#>*\\-\\s]+", "").strip();
            if (plain.equals("待确认") || plain.equals("待确认项") || plain.equals("待补充项")) {
                continue;
            }
            if (PENDING.matcher(plain).find()) {
                count++;
            }
        }
        return count;
    }

    private Set<String> extractStrongAssertions(String content) {
        Set<String> claims = new LinkedHashSet<>();
        for (Pattern pattern : STRONG_ASSERTION_PATTERNS) {
            Matcher matcher = pattern.matcher(safe(content));
            while (matcher.find() && claims.size() < 20) {
                claims.add(matcher.group().strip());
            }
        }
        return claims;
    }

    private boolean looksLikeMojibake(String content) {
        String value = safe(content);
        long c1Controls = value.codePoints().filter(code -> code >= 0x80 && code <= 0x9f).count();
        int commonSequences = 0;
        Matcher matcher = Pattern.compile("(?:ï¼|ã|â|Ã.|Â.)").matcher(value);
        while (matcher.find()) {
            commonSequences++;
        }
        return c1Controls >= 2 || commonSequences >= 3;
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[\\s,，]", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
