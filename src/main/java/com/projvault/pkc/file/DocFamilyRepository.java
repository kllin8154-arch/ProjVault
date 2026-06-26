package com.projvault.pkc.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocFamilyRepository extends JpaRepository<DocFamily, Long> {

    Page<DocFamily> findByProjectIdOrderByFileCountDescFamilyNameAsc(Long projectId, Pageable pageable);

    List<DocFamily> findByProjectId(Long projectId);

    Optional<DocFamily> findByProjectIdAndFamilyName(Long projectId, String familyName);

    void deleteByProjectId(Long projectId);
}
