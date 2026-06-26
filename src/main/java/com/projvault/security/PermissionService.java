package com.projvault.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 数据库 RBAC 权限校验，并持久化每次授权决策。
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    private final AuthService authService;
    private final PermissionAuditRepository auditRepository;
    @Value("${projvault.security.enabled:true}") private boolean enabled;

    public PermissionService(AuthService authService, PermissionAuditRepository auditRepository) {
        this.authService = authService;
        this.auditRepository = auditRepository;
    }

    public Decision check(HttpServletRequest request, String permCode) {
        Optional<RbacUser> current = authService.currentUser(request);
        boolean allowed = !enabled || current.map(user -> hasPermission(user, permCode)).orElse(false);
        audit(request, current.orElse(null), permCode, allowed);
        return new Decision(current.isPresent() || !enabled, allowed,
                current.map(RbacUser::getUsername).orElse("anonymous"));
    }

    private boolean hasPermission(RbacUser user, String required) {
        return user.getRoles().stream().flatMap(role -> role.getPermissions().stream()).anyMatch(granted ->
                "*".equals(granted) || granted.equals(required)
                        || (granted.endsWith("*") && required.startsWith(granted.substring(0, granted.length() - 1))));
    }

    private void audit(HttpServletRequest request, RbacUser user, String permission, boolean allowed) {
        try {
            PermissionAudit audit = new PermissionAudit();
            if (user != null) { audit.setUserId(user.getId()); audit.setUsername(user.getUsername()); }
            audit.setPermissionCode(permission); audit.setMethod(request.getMethod());
            audit.setPath(request.getRequestURI()); audit.setAllowed(allowed); audit.setRemoteAddress(request.getRemoteAddr());
            auditRepository.save(audit);
        } catch (Exception e) {
            log.error("RBAC 审计日志写入失败 permission={} path={}", permission, request.getRequestURI(), e);
        }
    }

    public record Decision(boolean authenticated, boolean allowed, String username) {}
}
