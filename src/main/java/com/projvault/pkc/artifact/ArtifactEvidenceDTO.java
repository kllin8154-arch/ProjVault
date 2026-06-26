package com.projvault.pkc.artifact;

public record ArtifactEvidenceDTO(
        Long chunkId,
        Long fileId,
        String fileName,
        String relPath,
        String headingPath,
        int pageNo,
        String cellRange,
        String excerpt,
        double score) {
}
