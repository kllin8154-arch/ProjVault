package com.projvault.pkc.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GraphNodeRepository extends JpaRepository<GraphNode, Long> {

    List<GraphNode> findByProjectId(Long projectId);

    List<GraphNode> findByProjectIdAndNodeType(Long projectId, String nodeType);

    @Modifying
    @Transactional
    @Query("DELETE FROM GraphNode g WHERE g.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    @Modifying
    @Transactional
    @Query("DELETE FROM GraphNode g WHERE g.projectId = :projectId AND g.nodeType <> 'ENTITY'")
    void deleteStructuralByProjectId(@Param("projectId") Long projectId);
}
