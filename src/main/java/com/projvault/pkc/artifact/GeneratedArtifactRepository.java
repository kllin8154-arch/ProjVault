package com.projvault.pkc.artifact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedArtifactRepository extends JpaRepository<GeneratedArtifact, Long> {

    List<GeneratedArtifact> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long projectId);

    List<GeneratedArtifact> findByProjectIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Long projectId);

    List<GeneratedArtifact> findByProjectIdAndRelativePathStartingWithAndDeletedAtIsNull(
            Long projectId, String relativePathPrefix);

    long countByProjectIdAndRelativePathStartingWithAndDeletedAtIsNull(
            Long projectId, String relativePathPrefix);

    GeneratedArtifact findTopByProjectIdAndRootArtifactIdOrderByVersionNoDesc(
            Long projectId, Long rootArtifactId);

    void deleteByProjectId(Long projectId);
}
