package com.projvault.pkc.scan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanChangeRepository extends JpaRepository<ScanChange, Long> {

    List<ScanChange> findByScanIdOrderByChangeTypeAscRelPathAsc(Long scanId);

    List<ScanChange> findByProjectIdOrderByScanIdDescCreatedAtDesc(Long projectId);

    void deleteByScanId(Long scanId);

    void deleteByProjectId(Long projectId);
}
