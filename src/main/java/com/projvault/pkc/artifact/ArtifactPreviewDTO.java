package com.projvault.pkc.artifact;

import java.util.List;

public record ArtifactPreviewDTO(
        GeneratedArtifactDTO artifact,
        String previewKind,
        String content,
        String editableContent,
        String inlineUrl,
        int pageCount,
        List<ArtifactEvidenceDTO> evidence,
        ArtifactQualityDTO quality) {
}
