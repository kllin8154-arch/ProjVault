package com.projvault.security;

import com.projvault.common.BusinessException;
import com.projvault.settings.UserAiSettingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SecurityAdminService {
    private final RbacUserRepository users;
    private final RbacRoleRepository roles;
    private final PermissionAuditRepository audits;
    private final PasswordHasher hasher;
    private final UserAiSettingRepository userAiSettings;

    public SecurityAdminService(RbacUserRepository users, RbacRoleRepository roles,
                                PermissionAuditRepository audits, PasswordHasher hasher,
                                UserAiSettingRepository userAiSettings) {
        this.users = users; this.roles = roles; this.audits = audits; this.hasher = hasher;
        this.userAiSettings = userAiSettings;
    }

    public List<RbacUserDTO> users() { return users.findAll().stream().map(RbacUserDTO::from).toList(); }
    public List<RbacRoleDTO> roles() { return roles.findAll().stream().map(RbacRoleDTO::from).toList(); }
    public List<PermissionAudit> audits(int size) { return audits.findAllByOrderByIdDesc(PageRequest.of(0, Math.min(Math.max(size, 1), 200))); }

    @Transactional
    public RbacUserDTO create(RbacUserRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) throw new BusinessException("新用户必须设置至少 8 位密码");
        if (users.findByUsername(request.getUsername().strip()).isPresent()) throw new BusinessException(409, "用户名已存在");
        RbacUser user = new RbacUser(); apply(user, request, true); return RbacUserDTO.from(users.save(user));
    }

    @Transactional
    public RbacUserDTO update(Long id, RbacUserRequest request) {
        RbacUser user = users.findById(id).orElseThrow(() -> new BusinessException(404, "用户不存在"));
        users.findByUsername(request.getUsername().strip()).filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new BusinessException(409, "用户名已存在"); });
        apply(user, request, false); return RbacUserDTO.from(users.save(user));
    }

    @Transactional
    public void delete(Long id) {
        RbacUser user = users.findById(id).orElseThrow(() -> new BusinessException(404, "用户不存在"));
        userAiSettings.deleteById(id);
        users.delete(user);
    }

    private void apply(RbacUser user, RbacUserRequest request, boolean creating) {
        user.setUsername(request.getUsername().strip()); user.setDisplayName(request.getDisplayName().strip()); user.setEnabled(request.isEnabled());
        if (creating || (request.getPassword() != null && !request.getPassword().isBlank())) {
            String salt = hasher.newSalt(); user.setPasswordSalt(salt); user.setPasswordHash(hasher.hash(request.getPassword(), salt));
        }
        List<RbacRole> selected = roles.findByCodeIn(request.getRoles());
        if (selected.size() != request.getRoles().size()) throw new BusinessException("包含不存在的角色");
        user.setRoles(new LinkedHashSet<>(selected));
    }
}
