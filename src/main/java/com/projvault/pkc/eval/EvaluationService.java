package com.projvault.pkc.eval;

import com.projvault.common.BusinessException;
import com.projvault.pkc.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EvaluationService {
    private final GoldenQuestionRepository questionRepository;
    private final EvaluationRunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final EvaluationRunner runner;

    public EvaluationService(GoldenQuestionRepository questionRepository,
                             EvaluationRunRepository runRepository,
                             ProjectRepository projectRepository,
                             EvaluationRunner runner) {
        this.questionRepository = questionRepository;
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.runner = runner;
    }

    public List<GoldenQuestion> questions(Long projectId) {
        requireProject(projectId);
        return questionRepository.findByProjectIdOrderByIdAsc(projectId);
    }

    @Transactional
    public GoldenQuestion create(Long projectId, GoldenQuestionRequest request) {
        requireProject(projectId);
        GoldenQuestion question = new GoldenQuestion();
        question.setProjectId(projectId);
        apply(question, request);
        return questionRepository.save(question);
    }

    @Transactional
    public GoldenQuestion update(Long id, GoldenQuestionRequest request) {
        GoldenQuestion question = getQuestion(id);
        apply(question, request);
        return questionRepository.save(question);
    }

    @Transactional
    public void delete(Long id) { questionRepository.delete(getQuestion(id)); }

    @Transactional
    public EvaluationRun start(Long projectId, Long requestedByUserId) {
        requireProject(projectId);
        int count = questionRepository.findByProjectIdAndActiveTrueOrderByIdAsc(projectId).size();
        if (count == 0) throw new BusinessException(422, "请先添加至少一道启用的黄金问题");
        EvaluationRun run = new EvaluationRun();
        run.setProjectId(projectId);
        run.setRequestedByUserId(requestedByUserId);
        run.setTotalQuestions(count);
        run = runRepository.save(run);
        Long id = run.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { runner.run(id); }
            });
        } else runner.run(id);
        return run;
    }

    public EvaluationRun getRun(Long id) {
        return runRepository.findById(id).orElseThrow(() -> new BusinessException(404, "评测运行不存在: " + id));
    }

    public List<EvaluationRun> runs(Long projectId) { return runRepository.findTop20ByProjectIdOrderByIdDesc(projectId); }

    private GoldenQuestion getQuestion(Long id) {
        return questionRepository.findById(id).orElseThrow(() -> new BusinessException(404, "黄金问题不存在: " + id));
    }

    private void apply(GoldenQuestion question, GoldenQuestionRequest request) {
        question.setQuestion(request.getQuestion().strip());
        question.setExpectedKeywords(request.getExpectedKeywords().strip());
        question.setExpectedSourcePatterns(request.getExpectedSourcePatterns() == null ? null : request.getExpectedSourcePatterns().strip());
        question.setMode(request.getMode() == null ? "standard" : request.getMode());
        question.setActive(request.isActive());
        question.setUpdatedAt(LocalDateTime.now());
    }

    private void requireProject(Long id) {
        if (!projectRepository.existsById(id)) throw new BusinessException(404, "项目不存在: " + id);
    }
}
