package com.projvault.pkc.artifact;

import java.util.List;

public record ArtifactQualityDTO(
        String status,
        int completenessScore,
        int evidenceCoverageScore,
        int pendingCount,
        List<String> suspectedUnsupportedFacts,
        List<String> formatChecks,
        List<String> warnings) {
}
