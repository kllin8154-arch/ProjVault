package com.projvault.pkc.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projvault.ai.RagCitation;
import com.projvault.ai.AiCallerContext;
import com.projvault.pkc.rag.RagAnswerWithTrace;
import com.projvault.pkc.rag.RagService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.projvault.security.RbacUserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class EvaluationRunner {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EvaluationRunRepository runRepository;
    private final GoldenQuestionRepository questionRepository;
    private final RagService ragService;
    private final RbacUserRepository userRepository;

    public EvaluationRunner(EvaluationRunRepository runRepository,
                            GoldenQuestionRepository questionRepository,
                            RagService ragService,
                            RbacUserRepository userRepository) {
        this.runRepository = runRepository;
        this.questionRepository = questionRepository;
        this.ragService = ragService;
        this.userRepository = userRepository;
    }

    @Async("evaluationExecutor")
    public void run(Long runId) {
        EvaluationRun run = runRepository.findById(runId).orElse(null);
        if (run == null) return;
        if (run.getRequestedByUserId() != null) {
            var user = userRepository.findById(run.getRequestedByUserId()).orElse(null);
            if (user == null || !user.isEnabled()) {
                failMissingCaller(run);
                return;
            }
            AiCallerContext.set(user.getId(), user.getRoles().stream()
                    .anyMatch(role -> "ADMIN".equals(role.getCode())));
        }
        long started = System.currentTimeMillis();
        run.setStatus("RUNNING");
        runRepository.save(run);
        try {
            List<EvaluationItemResult> results = new ArrayList<>();
            for (GoldenQuestion question : questionRepository
                    .findByProjectIdAndActiveTrueOrderByIdAsc(run.getProjectId())) {
                RagAnswerWithTrace answer = ragService.askWithTrace(
                        run.getProjectId(), question.getQuestion(), 12, question.getMode());
                if (providerUnavailable(answer)) {
                    throw new IllegalStateException("评测中止：回答模型暂时不可用，未把基础设施故障计为答案不合格");
                }
                results.add(score(question, answer));
            }
            run.setTotalQuestions(results.size());
            run.setPassedQuestions((int) results.stream().filter(EvaluationItemResult::passed).count());
            run.setAverageScore(results.stream().mapToDouble(EvaluationItemResult::score).average().orElse(0.0));
            run.setDetailsJson(MAPPER.writeValueAsString(results));
            run.setStatus("SUCCESS");
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            AiCallerContext.clear();
            run.setDurationMs(System.currentTimeMillis() - started);
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
        }
    }

    private void failMissingCaller(EvaluationRun run) {
        run.setStatus("FAILED");
        run.setErrorMessage("评测发起用户不存在或已停用");
        run.setFinishedAt(LocalDateTime.now());
        runRepository.save(run);
    }

    EvaluationItemResult score(GoldenQuestion question, RagAnswerWithTrace answer) {
        List<String> keywords = split(question.getExpectedKeywords());
        List<String> sourcePatterns = split(question.getExpectedSourcePatterns());
        String normalizedAnswer = normalize(answer.answer());
        List<String> missing = keywords.stream().filter(keyword -> !normalizedAnswer.contains(normalize(keyword))).toList();
        double keywordCoverage = keywords.isEmpty() ? 1.0 : (keywords.size() - missing.size()) / (double) keywords.size();
        List<String> citationFiles = answer.citations().stream().map(RagCitation::fileName).filter(java.util.Objects::nonNull).toList();
        List<String> matchedSources = sourcePatterns.stream()
                .filter(pattern -> citationFiles.stream().anyMatch(file -> normalize(file).contains(normalize(pattern))))
                .toList();
        double sourceCoverage = sourcePatterns.isEmpty() ? 1.0 : matchedSources.size() / (double) sourcePatterns.size();
        double score = (keywordCoverage * 0.65 + sourceCoverage * 0.25 + (answer.grounded() ? 0.1 : 0.0));
        boolean passed = answer.grounded() && keywordCoverage >= 0.7 && sourceCoverage >= 0.5;
        return new EvaluationItemResult(question.getId(), question.getQuestion(), passed, score,
                keywordCoverage, sourceCoverage, answer.grounded(), missing, matchedSources, answer.answer());
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[\\r\\n,，;；]+"))
                .map(String::strip).filter(item -> !item.isBlank()).distinct().toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。？！、；：「」『』【】《》（）]+", "");
    }

    private boolean providerUnavailable(RagAnswerWithTrace answer) {
        if (answer == null || answer.answer() == null) return true;
        String text = answer.answer();
        return text.contains("回答模型暂时不可用") || text.contains("模型调用失败");
    }
}
