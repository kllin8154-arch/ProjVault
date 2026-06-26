package com.projvault.pkc.eval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {
    List<EvaluationRun> findTop20ByProjectIdOrderByIdDesc(Long projectId);
    void deleteByProjectId(Long projectId);
}
