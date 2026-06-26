package com.projvault.pkc.scan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanTaskRepository extends JpaRepository<ScanTask, Long> {

    List<ScanTask> findByProjectIdOrderByIdDesc(Long projectId);

    List<ScanTask> findByStatusIn(List<ScanStatus> statuses);

    List<ScanTask> findByStatusInOrderByIdAsc(List<ScanStatus> statuses);

    boolean existsByProjectIdAndStatusIn(Long projectId, List<ScanStatus> statuses);

    void deleteByProjectId(Long projectId);
}
