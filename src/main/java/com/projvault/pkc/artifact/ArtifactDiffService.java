package com.projvault.pkc.artifact;

import com.projvault.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ArtifactDiffService {

    private static final int MAX_LINES = 1200;

    private final ArtifactService artifactService;
    private final ArtifactPreviewService previewService;

    public ArtifactDiffService(ArtifactService artifactService,
                               ArtifactPreviewService previewService) {
        this.artifactService = artifactService;
        this.previewService = previewService;
    }

    public ArtifactDiffDTO diff(Long targetArtifactId, Long requestedBaseId) {
        GeneratedArtifact target = artifactService.getEntity(targetArtifactId);
        Long baseId = requestedBaseId == null ? target.getParentArtifactId() : requestedBaseId;
        if (baseId == null) {
            throw new BusinessException(422, "该交付物没有可对比的上一版本");
        }
        GeneratedArtifact base = artifactService.getEntity(baseId);
        if (!base.getProjectId().equals(target.getProjectId())) {
            throw new BusinessException(422, "只能比较同一项目的交付物");
        }
        List<String> before = lines(previewService.extractEditableText(base));
        List<String> after = lines(previewService.extractEditableText(target));
        List<ArtifactDiffLine> diff = lcsDiff(before, after);
        int added = (int) diff.stream().filter(line -> line.type().equals("ADDED")).count();
        int removed = (int) diff.stream().filter(line -> line.type().equals("REMOVED")).count();
        int unchanged = diff.size() - added - removed;
        String granularity = target.getFormat().equals("PPTX") ? "SLIDE_LINE" : "PARAGRAPH";
        return new ArtifactDiffDTO(baseId, targetArtifactId, granularity,
                diff, added, removed, unchanged);
    }

    private List<String> lines(String text) {
        return text.lines().map(String::stripTrailing).limit(MAX_LINES).toList();
    }

    private List<ArtifactDiffLine> lcsDiff(List<String> before, List<String> after) {
        int[][] lcs = new int[before.size() + 1][after.size() + 1];
        for (int i = before.size() - 1; i >= 0; i--) {
            for (int j = after.size() - 1; j >= 0; j--) {
                lcs[i][j] = before.get(i).equals(after.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<ArtifactDiffLine> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < before.size() && j < after.size()) {
            if (before.get(i).equals(after.get(j))) {
                result.add(new ArtifactDiffLine("UNCHANGED", before.get(i)));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                result.add(new ArtifactDiffLine("REMOVED", before.get(i++)));
            } else {
                result.add(new ArtifactDiffLine("ADDED", after.get(j++)));
            }
        }
        while (i < before.size()) {
            result.add(new ArtifactDiffLine("REMOVED", before.get(i++)));
        }
        while (j < after.size()) {
            result.add(new ArtifactDiffLine("ADDED", after.get(j++)));
        }
        return Collections.unmodifiableList(result);
    }
}
