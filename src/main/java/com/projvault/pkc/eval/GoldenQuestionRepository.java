package com.projvault.pkc.eval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoldenQuestionRepository extends JpaRepository<GoldenQuestion, Long> {
    List<GoldenQuestion> findByProjectIdOrderByIdAsc(Long projectId);
    List<GoldenQuestion> findByProjectIdAndActiveTrueOrderByIdAsc(Long projectId);
    void deleteByProjectId(Long projectId);
}
