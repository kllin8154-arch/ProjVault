package com.projvault.security;

import com.projvault.common.BusinessException;
import com.projvault.pkc.project.Project;
import com.projvault.pkc.project.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectAccessService {
    private final AuthService authService;
    private final ProjectRepository projectRepository;

    public ProjectAccessService(AuthService authService, ProjectRepository projectRepository) {
        this.authService = authService;
        this.projectRepository = projectRepository;
    }

    public RbacUser currentUser(HttpServletRequest request) {
        return authService.currentUser(request)
                .orElseThrow(() -> new BusinessException(401, "未登录"));
    }

    public boolean isAdmin(RbacUser user) {
        return user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode()));
    }

    public List<Project> visibleProjects(HttpServletRequest request) {
        RbacUser user = currentUser(request);
        return isAdmin(user) ? projectRepository.findAll()
                : projectRepository.findByOwnerUserIdOrderByIdAsc(user.getId());
    }

    public void requireProject(HttpServletRequest request, Long projectId) {
        RbacUser user = currentUser(request);
        if (isAdmin(user)) {
            return;
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在: " + projectId));
        if (!user.getId().equals(project.getOwnerUserId())) {
            throw new BusinessException(403, "无权访问该项目");
        }
    }
}
