package com.projvault.pkc.rag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AskHistoryRepository extends JpaRepository<AskHistory, Long> {

    Page<AskHistory> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    long countByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}
