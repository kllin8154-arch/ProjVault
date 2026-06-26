package com.projvault.pkc.scan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScanReportRepository extends JpaRepository<ScanReport, Long> {

    Optional<ScanReport> findByScanId(Long scanId);

    Optional<ScanReport> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    void deleteByProjectId(Long projectId);
}
