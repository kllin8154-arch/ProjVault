package com.projvault.security;

import com.projvault.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pkc/security")
public class SecurityAdminController {
    private final SecurityAdminService service;
    public SecurityAdminController(SecurityAdminService service) { this.service = service; }
    @GetMapping("/users") @RequirePerm("pkc:security:manage") public ApiResponse<List<RbacUserDTO>> users() { return ApiResponse.ok(service.users()); }
    @PostMapping("/users") @RequirePerm("pkc:security:manage") public ApiResponse<RbacUserDTO> create(@Valid @RequestBody RbacUserRequest request) { return ApiResponse.ok(service.create(request)); }
    @PutMapping("/users/{id}") @RequirePerm("pkc:security:manage") public ApiResponse<RbacUserDTO> update(@PathVariable Long id, @Valid @RequestBody RbacUserRequest request) { return ApiResponse.ok(service.update(id, request)); }
    @DeleteMapping("/users/{id}") @RequirePerm("pkc:security:manage") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(null); }
    @GetMapping("/roles") @RequirePerm("pkc:security:manage") public ApiResponse<List<RbacRoleDTO>> roles() { return ApiResponse.ok(service.roles()); }
    @GetMapping("/audits") @RequirePerm("pkc:security:manage") public ApiResponse<List<PermissionAudit>> audits(@RequestParam(defaultValue = "100") int size) { return ApiResponse.ok(service.audits(size)); }
}
