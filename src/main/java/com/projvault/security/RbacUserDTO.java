package com.projvault.security;

import java.time.LocalDateTime;
import java.util.List;

public record RbacUserDTO(Long id, String username, String displayName, boolean enabled,
                          List<String> roles, LocalDateTime createdAt) {
    public static RbacUserDTO from(RbacUser user) {
        return new RbacUserDTO(user.getId(), user.getUsername(), user.getDisplayName(), user.isEnabled(),
                user.getRoles().stream().map(RbacRole::getCode).sorted().toList(), user.getCreatedAt());
    }
}
