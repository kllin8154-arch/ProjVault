package com.projvault.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RbacRoleRepository extends JpaRepository<RbacRole, Long> {
    Optional<RbacRole> findByCode(String code);
    List<RbacRole> findByCodeIn(Collection<String> codes);
}
