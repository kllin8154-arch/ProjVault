package com.projvault.pkc.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projvault.ai.RagAnswer;
import com.projvault.ai.RagAnswerModelProvider;
import com.projvault.ai.RagCitation;
import com.projvault.ai.UserAwareRagAnswerProvider;
import com.projvault.pkc.file.DocChunk;
import com.projvault.pkc.file.FileAsset;
import com.projvault.pkc.file.FileAssetRepository;
import com.projvault.pkc.file.GraphCommunity;
import com.projvault.pkc.file.GraphCommunityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final String MODEL_UNAVAILABLE_MESSAGE =
            "检索已完成，但回答模型暂时不可用。请稍后重试；本次不会把模型故障误判为“资料中没有答案”。";

    private final ChunkRetriever retriever;
    private final RagAnswerModelProvider ragAnswerModelProvider;
    private final FileAssetRepository fileAssetRepository;
    private final AskHistoryRepository askHistoryRepository;
    private final LocalSearchExpander localSearchExpander;
    private final GraphCommunityRepository graphCommunityRepository;

    public RagService(ChunkRetriever retriever,
                      RagAnswerModelProvider ragAnswerModelProvider,
                      FileAssetRepository fileAssetRepository,
                      AskHistoryRepository askHistoryRepository,
                      LocalSearchExpander localSearchExpander,
                      GraphCommunityRepository graphCommunityRepository) {
        this.retriever = retriever;
        this.ragAnswerModelProvider = ragAnswerModelProvider;
        this.fileAssetRepository = fileAssetRepository;
        this.askHistoryRepository = askHistoryRepository;
        this.localSearchExpander = localSearchExpander;
        this.graphCommunityRepository = graphCommunityRepository;
    }

    public RagAnswer ask(Long projectId, String question, int topK) {
        return ask(projectId, question, topK, "standard");
    }

    public RagAnswer ask(Long projectId, String question, int topK, String mode) {
        return askInternal(projectId, question, topK, mode).answer();
    }

    public RagAnswerWithTrace askWithTrace(Long projectId, String question, int topK, String mode) {
        AskResult result = askInternal(projectId, question, topK, mode);
        return RagAnswerWithTrace.from(result.answer(), result.trace());
    }

    public RagAnswer answerFromContexts(String instruction, List<String> contexts) {
        return requestModelAnswer(instruction, contexts);
    }

    private AskResult askInternal(Long projectId, String question, int topK, String mode) {
        String actualMode = (mode == null || mode.isBlank()) ? "standard" : mode;
        if ("global".equals(actualMode)) {
            List<ScoredChunk> scored = retriever.retrieve(projectId, question, Math.max(topK, 10));
            Map<Long, FileAsset> fileMap = loadFileMap(scored);
            RagAnswer global = globalAnswer(projectId, question, scored, fileMap);
            if (global != null) {
                saveHistory(projectId, question, topK, global);
                return new AskResult(global, buildTrace(actualMode, scored, LocalSearchResult.empty(), fileMap));
            }
            RagAnswer hint = new RagAnswer(
                    "全局总览需要知识社区数据：请先在扫描区打开“实体抽取”开关并触发一次全量扫描，"
                            + "完成后会生成实体图谱与社区摘要，再使用“全局总览”提问宏观主题类问题。",
                    List.of(),
                    false);
            saveHistory(projectId, question, topK, hint);
            return new AskResult(hint, AskTrace.empty(actualMode));
        }

        List<ScoredChunk> scored = retriever.retrieve(projectId, question, Math.max(topK, 10));
        log.info("[rag] project={} mode={} topK={} retrieved={} question={}",
                projectId, actualMode, topK, scored.size(), question);

        if (scored.isEmpty() && !"local".equals(actualMode)) {
            RagAnswer empty = emptyAnswer();
            saveHistory(projectId, question, topK, empty);
            return new AskResult(empty, AskTrace.empty(actualMode));
        }

        LocalSearchResult localResult = LocalSearchResult.empty();
        List<ScoredChunk> working = new ArrayList<>(scored);
        if ("local".equals(actualMode)) {
            List<DocChunk> seedChunks = scored.stream().map(ScoredChunk::chunk).toList();
            localResult = localSearchExpander.expand(projectId, question, seedChunks, Math.max(topK, 5));
            for (DocChunk chunk : localResult.chunks()) {
                working.add(new ScoredChunk(chunk, 0.45));
            }
            working = dedupeScored(working);
            log.info("[rag] local expanded seed={} graphFacts={} extraChunks={} total={}",
                    scored.size(), localResult.graphFacts().size(), localResult.chunks().size(), working.size());
        }

        if (working.isEmpty() && !localResult.hasEvidence()) {
            RagAnswer empty = emptyAnswer();
            saveHistory(projectId, question, topK, empty);
            return new AskResult(empty, AskTrace.empty(actualMode));
        }

        Map<Long, FileAsset> fileMap = loadFileMap(working);
        List<String> contextTexts = buildContextTexts(working, fileMap, localResult);
        RagAnswer modelAnswer = requestModelAnswer(question, contextTexts);

        if (modelAnswer.grounded()
                && looksLikeNoAnswer(modelAnswer.answer())
                && !"local".equals(actualMode)) {
            List<DocChunk> seedChunks = working.stream().map(ScoredChunk::chunk).toList();
            LocalSearchResult fallback = localSearchExpander.expand(projectId, question, seedChunks, Math.max(topK, 10));
            if (fallback.hasEvidence()) {
                localResult = fallback;
                for (DocChunk chunk : fallback.chunks()) {
                    working.add(new ScoredChunk(chunk, 0.45));
                }
                working = dedupeScored(working);
                fileMap = loadFileMap(working);
                contextTexts = buildContextTexts(working, fileMap, localResult);
                modelAnswer = requestModelAnswer(question, contextTexts);
                log.info("[rag] fallback local mode={} graphFacts={} extraChunks={} total={}",
                        actualMode, localResult.graphFacts().size(), localResult.chunks().size(), working.size());
            }
        }

        AskTrace trace = buildTrace(actualMode, working, localResult, fileMap);
        boolean grounded = modelAnswer.grounded() && !looksLikeNoAnswer(modelAnswer.answer());
        List<RagCitation> citations = grounded
                ? buildCitations(working, fileMap, modelAnswer.answer())
                : List.of();
        RagAnswer result = new RagAnswer(modelAnswer.answer(), citations, grounded);
        saveHistory(projectId, question, topK, result);
        return new AskResult(result, trace);
    }

    private RagAnswer globalAnswer(Long projectId,
                                   String question,
                                   List<ScoredChunk> scored,
                                   Map<Long, FileAsset> fileMap) {
        List<GraphCommunity> communities = graphCommunityRepository.findByProjectIdOrderBySizeDesc(projectId);
        if (communities.isEmpty() && scored.isEmpty()) {
            return null;
        }
        List<String> contexts = new ArrayList<>(buildContextTexts(scored, fileMap, LocalSearchResult.empty()));
        contexts.addAll(communities.stream()
                .map(c -> "【社区 " + c.getCommunityNo() + "，成员 " + c.getMembers() + "】\n" + c.getSummary())
                .toList());
        log.info("[rag] global project={} communities={} chunks={} question={}",
                projectId, communities.size(), scored.size(), question);
        RagAnswer modelAnswer = requestModelAnswer(question, contexts);
        boolean grounded = modelAnswer.grounded() && !looksLikeNoAnswer(modelAnswer.answer());
        return new RagAnswer(modelAnswer.answer(),
                grounded ? buildCitations(scored, fileMap, modelAnswer.answer()) : List.of(),
                grounded);
    }

    private Map<Long, FileAsset> loadFileMap(List<ScoredChunk> working) {
        List<Long> fileIds = working.stream()
                .map(sc -> sc.chunk().getFileId())
                .distinct()
                .toList();
        return fileAssetRepository.findAllById(fileIds)
                .stream()
                .collect(Collectors.toMap(FileAsset::getId, f -> f));
    }

    private List<String> buildContextTexts(List<ScoredChunk> working,
                                           Map<Long, FileAsset> fileMap,
                                           LocalSearchResult localResult) {
        List<String> contexts = new ArrayList<>();
        if (!localResult.graphFacts().isEmpty()) {
            contexts.add("【GraphRAG Local 图谱事实】\n" + String.join("\n", localResult.graphFacts()));
        }
        for (ScoredChunk scoredChunk : working) {
            DocChunk chunk = scoredChunk.chunk();
            FileAsset file = fileMap.get(chunk.getFileId());
            String src = file != null ? file.getRelPath() : "unknown";
            String heading = chunk.getHeadingPath() != null ? " § " + chunk.getHeadingPath() : "";
            contexts.add("【来源：" + src + heading + "】\n" + chunk.getContent());
        }
        return contexts;
    }

    private AskTrace buildTrace(String mode,
                                List<ScoredChunk> working,
                                LocalSearchResult localResult,
                                Map<Long, FileAsset> fileMap) {
        Map<Long, DocChunk> traceChunks = new LinkedHashMap<>();
        for (ScoredChunk scoredChunk : working) {
            traceChunks.putIfAbsent(scoredChunk.chunk().getId(), scoredChunk.chunk());
        }
        for (DocChunk chunk : localResult.chunks()) {
            traceChunks.putIfAbsent(chunk.getId(), chunk);
        }
        List<AskTrace.EvidenceChunk> evidence = traceChunks.values().stream()
                .map(chunk -> {
                    FileAsset file = fileMap.get(chunk.getFileId());
                    String fileName = file != null ? file.getRelPath() : "unknown";
                    return new AskTrace.EvidenceChunk(
                            chunk.getId(),
                            fileName,
                            chunk.getHeadingPath(),
                            preview(chunk.getContent()));
                })
                .toList();
        return new AskTrace(mode, localResult.graphFacts(), evidence);
    }

    private List<RagCitation> buildCitations(List<ScoredChunk> working,
                                             Map<Long, FileAsset> fileMap,
                                             String answer) {
        double maxScore = working.stream().mapToDouble(ScoredChunk::score).max().orElse(0.0);
        double threshold = citationThreshold(maxScore);
        Map<String, RagCitation> byFile = new LinkedHashMap<>();
        List<ScoredChunk> ranked = working.stream()
                .sorted(java.util.Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();
        for (ScoredChunk scoredChunk : ranked) {
            DocChunk chunk = scoredChunk.chunk();
            FileAsset file = fileMap.get(chunk.getFileId());
            if (scoredChunk.score() < threshold && !answerNamesFile(answer, file)) {
                continue;
            }
            String fileName = file != null ? file.getRelPath() : "unknown";
            RagCitation citation = new RagCitation(
                    fileName,
                    chunk.getHeadingPath(),
                    chunk.getId(),
                    scoredChunk.score());
            RagCitation existing = byFile.get(fileName);
            if (existing == null || citation.score() > existing.score()) {
                byFile.put(fileName, citation);
            }
            if (byFile.size() >= 6) {
                break;
            }
        }
        return new ArrayList<>(byFile.values());
    }

    static boolean answerNamesFile(String answer, FileAsset file) {
        if (answer == null || answer.isBlank() || file == null) {
            return false;
        }
        if (file.getName() != null && !file.getName().isBlank() && answer.contains(file.getName())) {
            return true;
        }
        return file.getRelPath() != null
                && !file.getRelPath().isBlank()
                && answer.contains(file.getRelPath());
    }

    RagAnswer requestModelAnswer(String question, List<String> contexts) {
        RagAnswer first = ragAnswerModelProvider.answer(question, contexts);
        if (isPersonalConfigRequired(first)) {
            return first;
        }
        if (!isProviderFailure(first)) {
            return first;
        }
        if (!isRetryableProviderFailure(first)) {
            return providerUnavailableAnswer();
        }
        log.warn("[rag] 回答模型出现可重试故障，正在重试一次");
        RagAnswer retry = ragAnswerModelProvider.answer(question, contexts);
        if (!isProviderFailure(retry)) {
            return retry;
        }
        return providerUnavailableAnswer();
    }

    static boolean isProviderFailure(RagAnswer answer) {
        if (answer == null || answer.answer() == null || answer.answer().isBlank()) {
            return true;
        }
        return !answer.grounded() && !looksLikeNoAnswerText(answer.answer());
    }

    static boolean isPersonalConfigRequired(RagAnswer answer) {
        return answer != null && UserAwareRagAnswerProvider.CONFIG_REQUIRED_MESSAGE.equals(answer.answer());
    }

    static boolean isRetryableProviderFailure(RagAnswer answer) {
        if (answer == null || answer.answer() == null || answer.answer().isBlank()) {
            return true;
        }
        if (answer.grounded()) {
            return false;
        }
        String message = answer.answer().toLowerCase();
        return message.contains("timeout") || message.contains("timed out")
                || message.contains("connection reset") || message.contains("connection refused")
                || message.contains("429") || message.contains("502") || message.contains("503")
                || message.contains("504") || message.contains("超时") || message.contains("连接重置")
                || message.contains("连接失败");
    }

    private RagAnswer providerUnavailableAnswer() {
        return new RagAnswer(MODEL_UNAVAILABLE_MESSAGE, List.of(), false);
    }

    static double citationThreshold(double maxScore) {
        if (maxScore <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.min(maxScore, Math.max(0.35, maxScore * 0.4));
    }

    private List<ScoredChunk> dedupeScored(List<ScoredChunk> chunks) {
        Map<Long, ScoredChunk> byChunkId = new LinkedHashMap<>();
        for (ScoredChunk scoredChunk : chunks) {
            Long chunkId = scoredChunk.chunk().getId();
            ScoredChunk existing = byChunkId.get(chunkId);
            if (existing == null || scoredChunk.score() > existing.score()) {
                byChunkId.put(chunkId, scoredChunk);
            }
        }
        return new ArrayList<>(byChunkId.values());
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ").strip();
        return compact.length() > 220 ? compact.substring(0, 220) + "..." : compact;
    }

    private RagAnswer emptyAnswer() {
        return new RagAnswer("资料中未找到与问题相关的内容，请尝试换一种描述方式。", List.of(), false);
    }

    private boolean looksLikeNoAnswer(String answer) {
        return looksLikeNoAnswerText(answer);
    }

    private static boolean looksLikeNoAnswerText(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        String compact = answer.replaceAll("\\s+", "");
        return compact.contains("资料中未找到相关内容")
                || compact.contains("资料中未找到与问题相关")
                || compact.contains("未找到相关内容")
                || compact.contains("没有直接提及")
                || compact.contains("无法基于给定内容")
                || compact.contains("无法准确回答")
                || compact.contains("无法判断");
    }

    private void saveHistory(Long projectId, String question, int topK, RagAnswer answer) {
        try {
            AskHistory history = new AskHistory();
            history.setProjectId(projectId);
            history.setQuestion(question);
            history.setAnswer(answer.answer());
            history.setGrounded(answer.grounded());
            history.setTopK(topK);
            history.setCitationCount(answer.citations() != null ? answer.citations().size() : 0);
            history.setCitationsJson(MAPPER.writeValueAsString(answer.citations()));
            askHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("[rag] 问答历史落库失败，不影响主流程: {}", e.getMessage());
        }
    }

    private record AskResult(RagAnswer answer, AskTrace trace) {}
}
