package com.projvault.pkc.artifact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtifactFolderRepository extends JpaRepository<ArtifactFolder, Long> {

    List<ArtifactFolder> findByProjectIdOrderByDefaultFolderDescNameAsc(Long projectId);

    Optional<ArtifactFolder> findByProjectIdAndRelativePath(Long projectId, String relativePath);

    List<ArtifactFolder> findByProjectIdAndRelativePathStartingWith(Long projectId, String prefix);

    void deleteByProjectId(Long projectId);
}
