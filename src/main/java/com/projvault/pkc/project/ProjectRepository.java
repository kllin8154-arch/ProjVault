package com.projvault.pkc.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByCode(String code);

    boolean existsByCode(String code);

    List<Project> findByOwnerUserIdOrderByIdAsc(Long ownerUserId);

    List<Project> findByOwnerUserIdIsNull();
}
