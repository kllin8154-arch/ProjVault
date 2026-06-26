package com.projvault.pkc.rag;

import com.projvault.pkc.file.DocChunk;
import com.projvault.pkc.file.FileAsset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地混合重排器：融合词法召回和字符 n-gram 语义近似，不依赖外部向量服务。
 */
final class HybridReranker {

    private static final int SEMANTIC_POOL_LIMIT = 80;
    private static final int PER_FILE_LIMIT = 3;

    List<ScoredChunk> rerank(String question,
                             List<String> keywords,
                             Map<Long, DocChunk> lexicalChunks,
                             Map<Long, Double> lexicalScores,
                             List<DocChunk> semanticPool,
                             Map<Long, FileAsset> files,
                             int topK) {
        String normalizedQuestion = normalize(question);
        Map<Long, Double> semanticScores = semanticPool.stream()
                .map(chunk -> Map.entry(chunk.getId(), semanticScore(normalizedQuestion, chunk, files.get(chunk.getFileId()))))
                .filter(entry -> entry.getValue() >= 0.08)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(SEMANTIC_POOL_LIMIT)
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);

        Map<Long, DocChunk> candidates = new LinkedHashMap<>(lexicalChunks);
        Map<Long, DocChunk> poolById = new HashMap<>();
        semanticPool.forEach(chunk -> poolById.put(chunk.getId(), chunk));
        semanticScores.keySet().forEach(id -> candidates.putIfAbsent(id, poolById.get(id)));
        if (candidates.isEmpty()) {
            return List.of();
        }

        double lexicalMax = lexicalScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        List<ScoredChunk> ranked = new ArrayList<>();
        for (DocChunk chunk : candidates.values()) {
            if (chunk == null) {
                continue;
            }
            double lexical = lexicalScores.getOrDefault(chunk.getId(), 0.0) / Math.max(lexicalMax, 0.0001);
            double semantic = semanticScores.getOrDefault(chunk.getId(),
                    semanticScore(normalizedQuestion, chunk, files.get(chunk.getFileId())));
            double coverage = keywordCoverage(keywords, chunk, files.get(chunk.getFileId()));
            double exact = normalize(textOf(chunk, files.get(chunk.getFileId()))).contains(normalizedQuestion)
                    && normalizedQuestion.length() >= 3 ? 0.12 : 0.0;
            double score = Math.min(1.0, lexical * 0.52 + semantic * 0.33 + coverage * 0.15 + exact);
            if (score >= 0.06) {
                ranked.add(new ScoredChunk(chunk, score));
            }
        }
        ranked.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        Map<Long, Integer> perFile = new HashMap<>();
        List<ScoredChunk> result = new ArrayList<>();
        for (ScoredChunk candidate : ranked) {
            Long fileId = candidate.chunk().getFileId();
            if (perFile.getOrDefault(fileId, 0) >= PER_FILE_LIMIT) {
                continue;
            }
            result.add(candidate);
            perFile.merge(fileId, 1, Integer::sum);
            if (result.size() >= topK) {
                break;
            }
        }
        return result;
    }

    double semanticScore(String normalizedQuestion, DocChunk chunk, FileAsset file) {
        if (normalizedQuestion.isBlank()) {
            return 0.0;
        }
        String text = normalize(textOf(chunk, file));
        if (text.isBlank()) {
            return 0.0;
        }
        Set<String> queryNgrams = ngrams(normalizedQuestion, 2);
        Set<String> textNgrams = ngrams(text, 2);
        if (queryNgrams.isEmpty() || textNgrams.isEmpty()) {
            return text.contains(normalizedQuestion) ? 1.0 : 0.0;
        }
        int intersection = 0;
        for (String gram : queryNgrams) {
            if (textNgrams.contains(gram)) {
                intersection++;
            }
        }
        double dice = (2.0 * intersection) / (queryNgrams.size() + textNgrams.size());
        double queryCoverage = intersection / (double) queryNgrams.size();
        return Math.min(1.0, dice * 0.35 + queryCoverage * 0.65);
    }

    private double keywordCoverage(List<String> keywords, DocChunk chunk, FileAsset file) {
        if (keywords.isEmpty()) {
            return 0.0;
        }
        String text = normalize(textOf(chunk, file));
        long hits = keywords.stream().map(this::normalize).filter(keyword -> keyword.length() >= 2)
                .filter(text::contains).distinct().count();
        return Math.min(1.0, hits / (double) Math.max(1, Math.min(keywords.size(), 8)));
    }

    private String textOf(DocChunk chunk, FileAsset file) {
        String metadata = file == null ? "" : file.getRelPath() + " " + file.getSummary();
        return metadata + " " + (chunk.getHeadingPath() == null ? "" : chunk.getHeadingPath())
                + " " + (chunk.getContent() == null ? "" : chunk.getContent());
    }

    private Set<String> ngrams(String text, int size) {
        Set<String> grams = new HashSet<>();
        int limit = Math.min(text.length(), 8000);
        for (int i = 0; i + size <= limit; i++) {
            grams.add(text.substring(i, i + size));
        }
        return grams;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[\\s\\p{Punct}，。？！、；：「」『』【】《》（）]+", "");
    }
}
