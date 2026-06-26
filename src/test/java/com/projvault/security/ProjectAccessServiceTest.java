package com.projvault.security;

import com.projvault.common.BusinessException;
import com.projvault.pkc.project.Project;
import com.projvault.pkc.project.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectAccessServiceTest {
    private final AuthService auth = mock(AuthService.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectAccessService service = new ProjectAccessService(auth, projects);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void administratorCanSeeAllProjects() {
        RbacUser admin = user(1L, "ADMIN");
        when(auth.currentUser(request)).thenReturn(Optional.of(admin));
        when(projects.findAll()).thenReturn(List.of(project(10L, 1L), project(11L, 2L)));

        assertThat(service.visibleProjects(request)).hasSize(2);
        verify(projects).findAll();
    }

    @Test
    void projectManagerOnlySeesOwnedProjects() {
        RbacUser manager = user(2L, "PROJECT_MANAGER");
        when(auth.currentUser(request)).thenReturn(Optional.of(manager));
        when(projects.findByOwnerUserIdOrderByIdAsc(2L)).thenReturn(List.of(project(11L, 2L)));

        assertThat(service.visibleProjects(request)).extracting(Project::getId).containsExactly(11L);
    }

    @Test
    void projectManagerCannotAccessAnotherOwnersProject() {
        RbacUser manager = user(2L, "PROJECT_MANAGER");
        when(auth.currentUser(request)).thenReturn(Optional.of(manager));
        when(projects.findById(10L)).thenReturn(Optional.of(project(10L, 1L)));

        assertThatThrownBy(() -> service.requireProject(request, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(403));
    }

    private RbacUser user(Long id, String roleCode) {
        RbacRole role = new RbacRole();
        role.setCode(roleCode);
        RbacUser user = new RbacUser();
        user.setId(id);
        user.setRoles(new LinkedHashSet<>(List.of(role)));
        return user;
    }

    private Project project(Long id, Long ownerId) {
        Project project = new Project();
        project.setId(id);
        project.setOwnerUserId(ownerId);
        return project;
    }
}
