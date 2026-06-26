package com.projvault.pkc.rag;

import java.util.List;

public record AskTrace(String mode, List<String> graphFacts, List<EvidenceChunk> evidenceChunks) {

    public static AskTrace empty(String mode) {
        return new AskTrace(mode, List.of(), List.of());
    }

    public boolean hasData() {
        return !graphFacts.isEmpty() || !evidenceChunks.isEmpty();
    }

    public record EvidenceChunk(Long chunkId, String fileName, String headingPath, String preview) {}
}
