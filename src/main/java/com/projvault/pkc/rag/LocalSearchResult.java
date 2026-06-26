package com.projvault.pkc.rag;

import com.projvault.pkc.file.DocChunk;

import java.util.List;

public record LocalSearchResult(List<DocChunk> chunks, List<String> graphFacts) {

    public static LocalSearchResult empty() {
        return new LocalSearchResult(List.of(), List.of());
    }

    public boolean hasEvidence() {
        return !chunks.isEmpty() || !graphFacts.isEmpty();
    }
}
