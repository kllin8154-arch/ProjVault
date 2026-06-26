package com.projvault.pkc.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ConfigItemRepository extends JpaRepository<ConfigItem, Long> {

    List<ConfigItem> findByProjectIdOrderByKeyType(Long projectId);

    List<ConfigItem> findByFileId(Long fileId);

    long countByProjectId(Long projectId);

    Page<ConfigItem> findByProjectId(Long projectId, Pageable pageable);

    Page<ConfigItem> findByProjectIdAndKeyType(Long projectId, String keyType, Pageable pageable);

    Page<ConfigItem> findByProjectIdAndReviewStatus(Long projectId, String reviewStatus, Pageable pageable);

    Page<ConfigItem> findByProjectIdAndKeyTypeAndReviewStatus(
            Long projectId, String keyType, String reviewStatus, Pageable pageable);

    @Query("SELECT c.keyType, COUNT(c) FROM ConfigItem c WHERE c.projectId = :projectId GROUP BY c.keyType")
    List<Object[]> countByKeyType(@Param("projectId") Long projectId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ConfigItem c WHERE c.fileId = :fileId")
    void deleteByFileId(@Param("fileId") Long fileId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ConfigItem c WHERE c.scanId = :scanId")
    void deleteByScanId(@Param("scanId") Long scanId);

    void deleteByProjectId(Long projectId);
}
