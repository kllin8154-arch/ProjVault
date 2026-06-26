package com.projvault.pkc.artifact;

import java.util.List;

public record ArtifactDiffDTO(
        Long baseArtifactId,
        Long targetArtifactId,
        String granularity,
        List<ArtifactDiffLine> lines,
        int added,
        int removed,
        int unchanged) {
}
