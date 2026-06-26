package com.projvault.pkc.file;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocChunkRepository extends JpaRepository<DocChunk, Long> {

    List<DocChunk> findByFileIdOrderBySeq(Long fileId);

    long countByFileId(Long fileId);

    @Modifying
    @Transactional
    @Query("DELETE FROM DocChunk c WHERE c.fileId = :fileId")
    void deleteByFileId(Long fileId);

    /**
     * 关键词检索：在指定项目的所有 chunk 中模糊匹配单个关键词。
     * fileId 通过子查询关联 FileAsset，仅检索未删除文件。
     */
    @Query("SELECT c FROM DocChunk c WHERE c.fileId IN " +
           "(SELECT f.id FROM FileAsset f WHERE f.projectId = :projectId AND f.deletedFlag = false) " +
           "AND LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<DocChunk> searchByKeyword(@Param("projectId") Long projectId,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    @Query("SELECT c FROM DocChunk c WHERE c.fileId IN " +
           "(SELECT f.id FROM FileAsset f WHERE f.projectId = :projectId AND f.deletedFlag = false) " +
           "ORDER BY c.fileId, c.seq")
    List<DocChunk> findByProjectIdPaged(@Param("projectId") Long projectId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM DocChunk c WHERE c.fileId IN (SELECT f.id FROM FileAsset f WHERE f.projectId = :projectId)")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
