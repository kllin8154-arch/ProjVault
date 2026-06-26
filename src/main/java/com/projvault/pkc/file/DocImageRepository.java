package com.projvault.pkc.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocImageRepository extends JpaRepository<DocImage, Long> {

    List<DocImage> findByFileIdOrderBySeq(Long fileId);

    @Modifying
    @Transactional
    @Query("DELETE FROM DocImage d WHERE d.fileId = :fileId")
    void deleteByFileId(@Param("fileId") Long fileId);

    @Modifying
    @Transactional
    @Query("DELETE FROM DocImage d WHERE d.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
