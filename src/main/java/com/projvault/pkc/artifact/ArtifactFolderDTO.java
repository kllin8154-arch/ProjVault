package com.projvault.pkc.artifact;

import java.time.LocalDateTime;

public record ArtifactFolderDTO(
        Long id,
        Long projectId,
        String name,
        String relativePath,
        String description,
        boolean defaultFolder,
        long artifactCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
