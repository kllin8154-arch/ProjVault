package com.projvault.pkc.rag;

import com.projvault.pkc.file.DocChunk;
import com.projvault.pkc.file.DocChunkRepository;
import com.projvault.pkc.file.FileAsset;
import com.projvault.pkc.file.FileAssetRepository;
import com.projvault.pkc.file.ProjectRelevanceService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关键词检索器（M1 版本）。
 * 将问题分词后，对 pkc_doc_chunk 做多轮 LIKE 查询，
 * 按关键词命中数打分，返回 topK 个最相关 chunk。
 * 接入真实向量模型后可替换本类，RagService 不需改动。
 */
@Service
public class ChunkRetriever {

    /** 不参与检索的停用词（中文高频词 + 英文介词） */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "或", "有", "为", "以",
            "对", "如", "到", "不", "该", "这", "那", "个", "么", "呢",
            "吗", "把", "被", "会", "能", "要", "可", "都", "就", "也",
            "但", "而", "从", "于", "上", "下", "中", "内", "外", "请",
            "问", "什", "怎", "哪", "如何", "什么",
            "the", "is", "of", "in", "at", "to", "a", "an", "and", "or",
            "for", "on", "with", "by", "be", "are", "was", "were", "has"
    );

    /** 每个关键词最多命中多少个 chunk */
    private static final int PER_KEYWORD_LIMIT = 30;
    private static final int PER_FILE_CHUNK_LIMIT = 3;
    private static final int SEMANTIC_CANDIDATE_LIMIT = 1200;

    private final DocChunkRepository docChunkRepository;
    private final FileAssetRepository fileAssetRepository;
    private final HybridReranker hybridReranker = new HybridReranker();

    public ChunkRetriever(DocChunkRepository docChunkRepository,
                          FileAssetRepository fileAssetRepository) {
        this.docChunkRepository = docChunkRepository;
        this.fileAssetRepository = fileAssetRepository;
    }

    /**
     * 检索并打分。
     *
     * @param projectId 项目 ID
     * @param question  用户问题
     * @param topK      返回数量
     * @return 按相关度降序的 ScoredChunk 列表
     */
    public List<ScoredChunk> retrieve(Long projectId, String question, int topK) {
        List<String> keywords = tokenize(question);
        if (keywords.isEmpty()) {
            return List.of();
        }

        // BM25-lite：IDF 词权 + 文档长度归一
        Map<Long, DocChunk> chunkMap = new LinkedHashMap<>();
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        Map<Long, FileAsset> fileMap = loadFileMap(projectId);

        for (String kw : keywords) {
            List<DocChunk> hits = docChunkRepository.searchByKeyword(
                    projectId, kw, PageRequest.of(0, PER_KEYWORD_LIMIT));
            int df = hits.size();
            // 稀有词权重高；几乎处处出现的常见词（df 大）权重低
            double w = 1.0 / Math.log(2.0 + df);
            for (DocChunk chunk : hits) {
                FileAsset file = fileMap.get(chunk.getFileId());
                if (!usableForRetrieval(file, question)) {
                    continue;
                }
                chunkMap.putIfAbsent(chunk.getId(), chunk);
                scoreMap.merge(chunk.getId(), w * relevanceBoost(file), Double::sum);
            }
        }
        scoreMetadataHits(projectId, question, keywords, fileMap, chunkMap, scoreMap);
        // 文档长度归一：抑制长 chunk 仅靠命中更多词而霸榜
        double max = 0.0;
        Map<Long, Double> normMap = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> e : scoreMap.entrySet()) {
            DocChunk c = chunkMap.get(e.getKey());
            int len = (c.getContent() == null) ? 1 : c.getContent().length();
            double s = e.getValue() / (1.0 + Math.log(1.0 + len / 200.0));
            normMap.put(e.getKey(), s);
            if (s > max) {
                max = s;
            }
        }
        final double mx = (max <= 0.0) ? 1.0 : max;

        Map<Long, Double> normalizedLexical = new LinkedHashMap<>();
        normMap.forEach((id, score) -> normalizedLexical.put(id, score / mx));
        List<DocChunk> semanticPool = docChunkRepository
                .findByProjectIdPaged(projectId, PageRequest.of(0, SEMANTIC_CANDIDATE_LIMIT)).stream()
                .filter(chunk -> usableForRetrieval(fileMap.get(chunk.getFileId()), question))
                .toList();
        return hybridReranker.rerank(question, keywords, chunkMap, normalizedLexical,
                semanticPool, fileMap, topK);
    }

    private void scoreMetadataHits(Long projectId,
                                   String question,
                                   List<String> keywords,
                                   Map<Long, FileAsset> fileMap,
                                   Map<Long, DocChunk> chunkMap,
                                   Map<Long, Double> scoreMap) {
        String queryCore = normalizeQuery(question);
        for (FileAsset file : fileMap.values()) {
            if (!"PARSED".equals(file.getParseStatus())) {
                continue;
            }
            if (!usableForRetrieval(file, question)) {
                continue;
            }
            String nameMeta = normalizeText(file.getRelPath() + " " + file.getName());
            String meta = normalizeText(nameMeta + " " + file.getSummary());
            double score = metadataScore(meta, nameMeta, queryCore, keywords) * relevanceBoost(file);
            if (score <= 0.0) {
                continue;
            }
            List<DocChunk> chunks = docChunkRepository.findByFileIdOrderBySeq(file.getId()).stream()
                    .limit(PER_FILE_CHUNK_LIMIT)
                    .toList();
            for (DocChunk chunk : chunks) {
                chunkMap.putIfAbsent(chunk.getId(), chunk);
                scoreMap.merge(chunk.getId(), score, Double::sum);
            }
        }
    }

    private Map<Long, FileAsset> loadFileMap(Long projectId) {
        Map<Long, FileAsset> files = new LinkedHashMap<>();
        for (FileAsset file : canonicalFiles(fileAssetRepository.findByProjectIdAndDeletedFlagFalse(projectId))) {
            files.put(file.getId(), file);
        }
        return files;
    }

    List<FileAsset> canonicalFiles(List<FileAsset> source) {
        Map<String, FileAsset> byHash = new LinkedHashMap<>();
        List<FileAsset> withoutHash = new ArrayList<>();
        for (FileAsset file : source) {
            if (file.getSha256() == null || file.getSha256().isBlank()) {
                withoutHash.add(file);
                continue;
            }
            byHash.merge(file.getSha256(), file, this::preferCanonical);
        }
        List<FileAsset> result = new ArrayList<>(byHash.values());
        result.addAll(withoutHash);
        return result;
    }

    private FileAsset preferCanonical(FileAsset left, FileAsset right) {
        int relevance = Double.compare(right.getRelevanceScore(), left.getRelevanceScore());
        if (relevance != 0) return relevance > 0 ? right : left;
        String leftPath = left.getRelPath() == null ? "" : left.getRelPath();
        String rightPath = right.getRelPath() == null ? "" : right.getRelPath();
        if (leftPath.length() != rightPath.length()) return leftPath.length() < rightPath.length() ? left : right;
        if (left.getId() == null) return right;
        if (right.getId() == null) return left;
        return left.getId() <= right.getId() ? left : right;
    }

    boolean usableForRetrieval(FileAsset file, String question) {
        if (file == null || ProjectRelevanceService.isOutOfScope(file)) {
            return false;
        }
        if (!ProjectRelevanceService.isReference(file)) {
            return true;
        }
        String q = normalizeText(question);
        return q.contains("模板") || q.contains("示例") || q.contains("样例") || q.contains("参考")
                || q.contains("\u6b63\u5f0f")
                || q.contains("\u751f\u4ea7")
                || q.contains("\u90e8\u7f72")
                || q.contains("\u5b9e\u9645")
                || q.contains("\u771f\u5b9e")
                || q.contains("\u670d\u52a1\u5668\u5730\u5740")
                || q.contains("\u6570\u636e\u5e93\u5730\u5740");
    }

    private double relevanceBoost(FileAsset file) {
        if (file == null || ProjectRelevanceService.isReference(file)) {
            return 0.35;
        }
        double score = file.getRelevanceScore();
        return score <= 0.0 ? 1.0 : Math.max(0.45, Math.min(1.2, score + 0.25));
    }

    double metadataScore(String meta, String nameMeta, String queryCore, List<String> keywords) {
        double score = 0.0;
        if (!queryCore.isBlank() && meta.contains(queryCore)) {
            score += 8.0;
        }
        if (!queryCore.isBlank() && nameMeta.contains(queryCore)) {
            score += 10.0;
        }
        for (String keyword : keywords) {
            String kw = normalizeText(keyword);
            if (kw.length() >= 2 && meta.contains(kw)) {
                score += kw.length() >= 4 ? 1.2 : 0.45;
            }
            if (kw.length() >= 2 && nameMeta.contains(kw)) {
                score += kw.length() >= 4 ? 1.2 : 0.45;
            }
        }
        return score >= 1.8 ? score : 0.0;
    }

    String normalizeQuery(String question) {
        String normalized = normalizeText(question);
        String[] suffixes = {"有哪些", "是什么", "有哪些？", "是什么？", "请列出", "请说明", "分别是什么"};
        for (String suffix : suffixes) {
            String s = normalizeText(suffix);
            if (normalized.endsWith(s)) {
                normalized = normalized.substring(0, normalized.length() - s.length());
            }
        }
        return normalized;
    }

    String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。？！、；：「」『』【】《》（）()\\[\\]{}_-]+", "");
    }

    /**
     * 简单分词：按空白/标点切分，过滤停用词和长度 < 2 的词。
     * 同时保留长度 >= 2 的中文子串（滑动窗口双字组合）。
     */
    List<String> tokenize(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        Set<String> result = new java.util.LinkedHashSet<>();

        // 按空格 / ASCII 标点 / 常见中文标点拆分（避免 Unicode 引号在字符类中的编译问题）
        String[] parts = question.split("[\\s\\p{Punct}，。？！、；：「-』【】《》]+");
        for (String part : parts) {
            String p = part.strip();
            if (p.length() < 2 || STOP_WORDS.contains(p.toLowerCase())) {
                continue;
            }
            result.add(p.toLowerCase());
        }

        // 中文双字滑动窗口（捕获"数据库"→"数据"+"据库"等双字组合）
        String clean = question.replaceAll("[\\s\\p{Punct}]", "");
        for (int i = 0; i + 1 < clean.length(); i++) {
            char c1 = clean.charAt(i);
            char c2 = clean.charAt(i + 1);
            if (isChinese(c1) && isChinese(c2)) {
                String bigram = "" + c1 + c2;
                if (!STOP_WORDS.contains(bigram)) {
                    result.add(bigram);
                }
            }
        }

        return new ArrayList<>(result);
    }

    private boolean isChinese(char c) {
        return c >= '一' && c <= '鿿';
    }
}
