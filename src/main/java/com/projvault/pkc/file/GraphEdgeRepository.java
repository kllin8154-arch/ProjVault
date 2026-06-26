package com.projvault.pkc.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GraphEdgeRepository extends JpaRepository<GraphEdge, Long> {

    List<GraphEdge> findByProjectId(Long projectId);

    List<GraphEdge> findByProjectIdAndEdgeType(Long projectId, String edgeType);

    @Modifying
    @Transactional
    @Query("DELETE FROM GraphEdge e WHERE e.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    @Modifying
    @Transactional
    @Query("DELETE FROM GraphEdge e WHERE e.projectId = :projectId AND e.edgeType <> 'ENTITY_REL'")
    void deleteStructuralByProjectId(@Param("projectId") Long projectId);

    @Modifying
    @Transactional
    @Query("DELETE FROM GraphEdge e WHERE e.projectId = :projectId " +
           "AND e.edgeType = 'ENTITY_REL' " +
           "AND e.evidenceChunkId IN (SELECT c.id FROM DocChunk c WHERE c.fileId IN :fileIds)")
    void deleteEntityEvidenceByFileIds(@Param("projectId") Long projectId,
                                       @Param("fileIds") List<Long> fileIds);
}
