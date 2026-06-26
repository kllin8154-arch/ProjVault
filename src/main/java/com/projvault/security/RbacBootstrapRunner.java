package com.projvault.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.Order;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@Order(10)
public class RbacBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RbacBootstrapRunner.class);
    private final RbacRoleRepository roles;
    private final RbacUserRepository users;
    private final PasswordHasher hasher;
    @Value("${projvault.security.bootstrap.username:admin}") private String username;
    @Value("${projvault.security.bootstrap.password:admin123}") private String password;

    public RbacBootstrapRunner(RbacRoleRepository roles, RbacUserRepository users, PasswordHasher hasher) {
        this.roles = roles; this.users = users; this.hasher = hasher;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        RbacRole admin = ensureRole("ADMIN", "系统管理员", Set.of("*"));
        ensureRole("PROJECT_MANAGER", "项目经理", Set.of(
                "pkc:project:view", "pkc:project:manage", "pkc:scan:start",
                "pkc:file:view", "pkc:file:read", "pkc:config:view", "pkc:config:review",
                "pkc:ai:configure",
                "pkc:artifact:generate", "pkc:artifact:manage", "pkc:artifact:review", "pkc:artifact:delete",
                "pkc:config:confirm", "pkc:evaluation:manage", "pkc:evaluation:run",
                "pkc:observability:view"));
        ensureRole("VIEWER", "只读成员", Set.of(
                "pkc:project:view", "pkc:file:view", "pkc:file:read", "pkc:config:view",
                "pkc:ai:configure"));
        if (users.findByUsername(username).isEmpty()) {
            var legacy = users.findByUsername("local-admin");
            if (legacy.isPresent()) {
                RbacUser user = legacy.get();
                user.setUsername(username);
                user.setDisplayName("系统管理员");
                String salt = hasher.newSalt();
                user.setPasswordSalt(salt);
                user.setPasswordHash(hasher.hash(password, salt));
                user.setRoles(new LinkedHashSet<>(Set.of(admin)));
                users.save(user);
                log.warn("已将旧管理员账号迁移为 {}，请及时修改初始密码。", username);
                return;
            }
            RbacUser user = new RbacUser();
            user.setUsername(username); user.setDisplayName("系统管理员");
            String salt = hasher.newSalt(); user.setPasswordSalt(salt); user.setPasswordHash(hasher.hash(password, salt));
            user.setRoles(new LinkedHashSet<>(Set.of(admin))); users.save(user);
            log.warn("已创建 RBAC 初始管理员 {}。请登录后立即修改由 PROJVAULT_ADMIN_PASSWORD 配置的初始密码。", username);
        }
    }

    private RbacRole ensureRole(String code, String name, Set<String> permissions) {
        RbacRole role = roles.findByCode(code).orElseGet(RbacRole::new);
        role.setCode(code);
        role.setName(name);
        role.setPermissions(new LinkedHashSet<>(permissions));
        return roles.save(role);
    }
}
