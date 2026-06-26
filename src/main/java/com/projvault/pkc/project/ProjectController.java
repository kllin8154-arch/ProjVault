package com.projvault.pkc.project;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import com.projvault.security.ProjectAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目档案接口（/api/pkc/projects）。
 */
@RestController
@RequestMapping("/api/pkc/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;

    public ProjectController(ProjectService projectService, ProjectAccessService projectAccessService) {
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
    }

    @PostMapping
    @RequirePerm("pkc:project:manage")
    public ApiResponse<Project> create(@Valid @RequestBody ProjectCreateRequest req,
                                       HttpServletRequest request) {
        return ApiResponse.ok(projectService.create(req, projectAccessService.currentUser(request).getId()));
    }

    @PutMapping("/{id}")
    @RequirePerm("pkc:project:manage")
    public ApiResponse<Project> update(@PathVariable Long id,
                                       @Valid @RequestBody ProjectUpdateRequest req) {
        return ApiResponse.ok(projectService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("pkc:project:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping
    @RequirePerm("pkc:project:view")
    public ApiResponse<List<Project>> list(HttpServletRequest request) {
        return ApiResponse.ok(projectAccessService.visibleProjects(request));
    }

    @GetMapping("/{id}")
    @RequirePerm("pkc:project:view")
    public ApiResponse<Project> get(@PathVariable Long id) {
        return ApiResponse.ok(projectService.getById(id));
    }
}
