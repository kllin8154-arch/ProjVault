package com.projvault.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RbacUserRepository extends JpaRepository<RbacUser, Long> {
    Optional<RbacUser> findByUsername(String username);
}
