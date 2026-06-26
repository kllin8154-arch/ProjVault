package com.projvault.pkc.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GraphCommunityRepository extends JpaRepository<GraphCommunity, Long> {

    List<GraphCommunity> findByProjectIdOrderBySizeDesc(Long projectId);

    @Modifying
    @Transactional
    @Query("DELETE FROM GraphCommunity gc WHERE gc.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
