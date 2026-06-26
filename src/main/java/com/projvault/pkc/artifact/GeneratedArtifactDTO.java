package com.projvault.pkc.artifact;

import java.time.LocalDateTime;
import java.util.List;

public record GeneratedArtifactDTO(
        Long id,
        Long projectId,
        String artifactType,
        String format,
        String title,
        String relativePath,
        List<String> sourceFiles,
        int evidenceCount,
        String qualityStatus,
        boolean previewed,
        boolean canApprove,
        Long parentArtifactId,
        Long rootArtifactId,
        int versionNo,
        String reviewStatus,
        String reviewComment,
        long fileSize,
        String sha256,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime deletedAt,
        String originalRelativePath) {
}
