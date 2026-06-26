package com.projvault.pkc.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {

    List<FileAsset> findByProjectIdAndDeletedFlagFalse(Long projectId);

    long countByProjectIdAndDeletedFlagFalse(Long projectId);

    Optional<FileAsset> findByProjectIdAndSha256AndDeletedFlagTrue(Long projectId, String sha256);

    Page<FileAsset> findByProjectIdAndDeletedFlagFalse(Long projectId, Pageable pageable);

    Page<FileAsset> findByProjectIdAndDocTypeAndDeletedFlagFalse(
            Long projectId, String docType, Pageable pageable);

    Page<FileAsset> findByProjectIdAndParseStatusAndDeletedFlagFalse(
            Long projectId, String parseStatus, Pageable pageable);

    @Query("SELECT f.docType, COUNT(f) FROM FileAsset f WHERE f.projectId = :projectId AND f.deletedFlag = false GROUP BY f.docType")
    List<Object[]> countByDocType(@Param("projectId") Long projectId);

    List<FileAsset> findByFamilyIdOrderByMtimeDesc(Long familyId);

    List<FileAsset> findByProjectIdAndSha256AndDeletedFlagFalse(Long projectId, String sha256);

    Optional<FileAsset> findByProjectIdAndRelPathAndDeletedFlagFalse(Long projectId, String relPath);

    void deleteByProjectId(Long projectId);
}
