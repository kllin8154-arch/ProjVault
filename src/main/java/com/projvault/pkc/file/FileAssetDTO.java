package com.projvault.pkc.file;

/**
 * 文件列表/详情响应 DTO（不暴露内部字段如 sha256）。
 */
public record FileAssetDTO(
        Long id,
        Long projectId,
        String relPath,
        String name,
        String ext,
        String category,
        long size,
        String parseStatus,
        String docType,
        String tags,
        String summary,
        String relevanceStatus,
        double relevanceScore,
        String relevanceReason,
        String scopeType,
        String scopeReason,
        int duplicateCount,
        java.util.List<String> duplicatePaths,
        boolean deletedFlag
) {
    public static FileAssetDTO from(FileAsset a) {
        return from(a, java.util.List.of());
    }

    public static FileAssetDTO from(FileAsset a, java.util.List<String> duplicatePaths) {
        java.util.List<String> paths = duplicatePaths == null ? java.util.List.of() : duplicatePaths;
        return new FileAssetDTO(
                a.getId(),
                a.getProjectId(),
                a.getRelPath(),
                a.getName(),
                a.getExt(),
                a.getCategory(),
                a.getSize(),
                a.getParseStatus(),
                a.getDocType(),
                a.getTags(),
                a.getSummary(),
                a.getRelevanceStatus(),
                a.getRelevanceScore(),
                a.getRelevanceReason(),
                a.getScopeType(),
                a.getScopeReason(),
                paths.size(),
                paths,
                a.isDeletedFlag()
        );
    }
}
