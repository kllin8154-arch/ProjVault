package com.projvault.security;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionAuditRepository extends JpaRepository<PermissionAudit, Long> {
    List<PermissionAudit> findAllByOrderByIdDesc(Pageable pageable);
}
