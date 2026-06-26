package com.projvault.security;

import java.util.List;

public record RbacRoleDTO(Long id, String code, String name, List<String> permissions) {
    public static RbacRoleDTO from(RbacRole role) {
        return new RbacRoleDTO(role.getId(), role.getCode(), role.getName(), role.getPermissions().stream().sorted().toList());
    }
}
