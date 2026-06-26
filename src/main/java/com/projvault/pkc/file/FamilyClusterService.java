package com.projvault.pkc.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Clusters document versions by normalized file name.
 */
@Service
public class FamilyClusterService {

    private static final Logger log = LoggerFactory.getLogger(FamilyClusterService.class);

    private static final Pattern PAT_VERSION =
            Pattern.compile("(?i)[_\\-\\s]*[Vv]\\d+(\\.\\d+)*");

    private static final Pattern PAT_DATE =
            Pattern.compile("\\b20\\d{6}\\b|\\b20\\d{2}-\\d{2}-\\d{2}\\b|\\b[01]\\d{3,5}\\b");

    private static final Pattern PAT_SUFFIX =
            Pattern.compile("(?i)[_\\-\\s]*(\\u7ec8\\u7248|\\u5b9a\\u7a3f|\\u6700\\u7ec8\\u7248|" +
                    "\\u6700\\u7ec8|final|\\u526f\\u672c|copy|revised|\\u4fee\\u8ba2\\u7248|" +
                    "\\u8bc4\\u5ba1\\u7248|\\u53d1\\u5e03\\u7248|\\u8349\\u7a3f|draft|" +
                    "\\u521d\\u7a3f|\\u4e8c\\u7a3f|\\u4e09\\u7a3f|\\u8bc4\\u5ba1|" +
                    "\\u9001\\u5ba1|\\u5f52\\u6863|\\u5907\\u4efd|backup|" +
                    "\\u542b\\u53d8\\u66f4\\u5907\\u6ce8|\\u53d8\\u66f4\\u5907\\u6ce8|" +
                    "\\u5907\\u6ce8\\u7248|\\u5907\\u6ce8)");

    private static final Pattern PAT_PAREN =
            Pattern.compile("[\\(\\uff08]\\d+[\\)\\uff09]");

    private static final Pattern PAT_SEP =
            Pattern.compile("[_\\-\\s]+");

    private static final List<Pattern> VERSION_LABEL_PATTERNS = List.of(
            Pattern.compile("(?i)[Vv]\\d+(\\.\\d+)*"),
            Pattern.compile("\\b20\\d{6}\\b"),
            Pattern.compile("\\u7ec8\\u7248|\\u5b9a\\u7a3f|\\u6700\\u7ec8\\u7248|\\u6700\\u7ec8|" +
                    "final|\\u526f\\u672c|copy|revised|\\u4fee\\u8ba2\\u7248|\\u8bc4\\u5ba1\\u7248|" +
                    "\\u53d1\\u5e03\\u7248|\\u8349\\u7a3f|draft|\\u521d\\u7a3f|\\u4e8c\\u7a3f|" +
                    "\\u4e09\\u7a3f|\\u542b\\u53d8\\u66f4\\u5907\\u6ce8|\\u53d8\\u66f4\\u5907\\u6ce8|" +
                    "\\u5907\\u6ce8\\u7248|\\u5907\\u6ce8")
    );

    private final FileAssetRepository fileAssetRepository;
    private final DocFamilyRepository docFamilyRepository;

    public FamilyClusterService(FileAssetRepository fileAssetRepository,
                                DocFamilyRepository docFamilyRepository) {
        this.fileAssetRepository = fileAssetRepository;
        this.docFamilyRepository = docFamilyRepository;
    }

    @Transactional
    public int clusterProject(Long projectId, Long scanId) {
        List<FileAsset> docs = fileAssetRepository
                .findByProjectIdAndDeletedFlagFalse(projectId)
                .stream()
                .filter(f -> "doc".equals(f.getCategory()))
                .collect(Collectors.toList());

        if (docs.isEmpty()) {
            log.info("[cluster] project={} has no doc files, skipped", projectId);
            return 0;
        }

        Map<String, List<FileAsset>> groups = new LinkedHashMap<>();
        for (FileAsset f : docs) {
            String key = normalizeName(f.getName());
            if (!key.isEmpty()) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
            }
        }

        int familiesCreated = 0;
        docFamilyRepository.deleteByProjectId(projectId);

        for (Map.Entry<String, List<FileAsset>> entry : groups.entrySet()) {
            String key = entry.getKey();
            List<FileAsset> members = entry.getValue();

            if (members.size() < 2) {
                FileAsset f = members.get(0);
                f.setFamilyId(null);
                f.setVersionLabel(null);
                fileAssetRepository.save(f);
                continue;
            }

            DocFamily family = new DocFamily();
            family.setProjectId(projectId);
            family.setLastScanId(scanId);
            family.setFamilyName(key);
            family.setFileCount(members.size());
            family.setCreatedAt(LocalDateTime.now());
            family.setUpdatedAt(LocalDateTime.now());

            members.stream()
                    .map(FileAsset::getDocType)
                    .filter(dt -> dt != null && !dt.isEmpty())
                    .collect(Collectors.groupingBy(dt -> dt, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .ifPresent(family::setDocType);

            members.stream()
                    .max(Comparator.comparingLong(FileAsset::getMtime))
                    .map(FileAsset::getId)
                    .ifPresent(family::setEffectiveFileId);

            DocFamily saved = docFamilyRepository.save(family);
            familiesCreated++;

            for (FileAsset f : members) {
                f.setFamilyId(saved.getId());
                f.setVersionLabel(extractVersionLabel(f.getName()));
                fileAssetRepository.save(f);
            }
        }

        log.info("[cluster] project={} completed, docFiles={}, families={}",
                projectId, docs.size(), familiesCreated);
        return familiesCreated;
    }

    String normalizeName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String s = fileName.replaceFirst("\\.[^.]+$", "");
        s = PAT_VERSION.matcher(s).replaceAll("");
        s = PAT_DATE.matcher(s).replaceAll("");
        s = PAT_SUFFIX.matcher(s).replaceAll("");
        s = PAT_PAREN.matcher(s).replaceAll("");
        s = PAT_SEP.matcher(s).replaceAll(" ").trim().toLowerCase();
        return s;
    }

    String extractVersionLabel(String fileName) {
        if (fileName == null) {
            return null;
        }
        String base = fileName.replaceFirst("\\.[^.]+$", "");
        for (Pattern p : VERSION_LABEL_PATTERNS) {
            Matcher m = p.matcher(base);
            if (m.find()) {
                return m.group().trim();
            }
        }
        return null;
    }
}
