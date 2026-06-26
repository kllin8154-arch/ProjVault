package com.projvault.pkc.artifact;

import com.projvault.common.BusinessException;
import com.projvault.pkc.file.FileAsset;
import com.projvault.pkc.rag.ScoredChunk;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ArtifactEvidenceScope {

    private static final Pattern EXCLUSIVE_SCOPE = Pattern.compile(
            "(?:仅|只)(?:使用|依据|基于|引用|采用|保留)?([^。；;\\n]{1,120})");
    private static final Pattern NUMBER_REFERENCE = Pattern.compile(
            "(?<![0-9-])(\\d{1,3})(?=\\s*(?:号|、|,|，|和|及))");
    private static final Pattern FILE_PREFIX = Pattern.compile("^(\\d{1,3})(?=\\D)");

    public List<ScoredChunk> apply(String instructions,
                                   List<ScoredChunk> evidence,
                                   List<FileAsset> projectFiles) {
        String scope = exclusiveScope(instructions);
        if (scope == null) {
            return evidence;
        }

        Set<Long> allowedFileIds = resolveAllowedFiles(scope, projectFiles);
        if (allowedFileIds.isEmpty()) {
            throw new BusinessException(422, "未找到编写要求限定的来源文件，请检查文件名称或编号");
        }

        List<ScoredChunk> scoped = evidence.stream()
                .filter(item -> allowedFileIds.contains(item.chunk().getFileId()))
                .toList();
        if (scoped.isEmpty()) {
            throw new BusinessException(422, "限定来源文件中没有可用于生成的文本证据");
        }
        return scoped;
    }

    String exclusiveScope(String instructions) {
        if (instructions == null || instructions.isBlank()) {
            return null;
        }
        Matcher matcher = EXCLUSIVE_SCOPE.matcher(instructions);
        return matcher.find() ? matcher.group(1).strip() : null;
    }

    Set<Long> resolveAllowedFiles(String scope, List<FileAsset> projectFiles) {
        Set<Integer> numericReferences = new LinkedHashSet<>();
        Matcher numberMatcher = NUMBER_REFERENCE.matcher(scope);
        while (numberMatcher.find()) {
            numericReferences.add(Integer.parseInt(numberMatcher.group(1)));
        }

        String normalizedScope = normalize(scope);
        Set<Long> allowed = new LinkedHashSet<>();
        for (FileAsset file : projectFiles) {
            String relPath = file.getRelPath();
            String fileName = Path.of(relPath).getFileName().toString();
            String stem = removeExtension(fileName);
            Matcher prefixMatcher = FILE_PREFIX.matcher(fileName);
            boolean numberMatch = prefixMatcher.find()
                    && numericReferences.contains(Integer.parseInt(prefixMatcher.group(1)));
            String normalizedStem = normalize(stem);
            boolean nameMatch = normalizedStem.length() >= 4
                    && (normalizedScope.contains(normalizedStem) || normalizedStem.contains(normalizedScope));
            if (numberMatch || nameMatch) {
                allowed.add(file.getId());
            }
        }
        return allowed;
    }

    private String removeExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。、《》【】（）()]+", "");
    }
}
