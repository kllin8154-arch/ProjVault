package com.projvault.pkc.eval;

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

@RestController
@RequestMapping("/api/pkc")
public class EvaluationController {
    private final EvaluationService service;
    private final ProjectAccessService accessService;
    public EvaluationController(EvaluationService service, ProjectAccessService accessService) {
        this.service = service;
        this.accessService = accessService;
    }

    @GetMapping("/projects/{projectId}/golden-questions") @RequirePerm("pkc:project:view")
    public ApiResponse<List<GoldenQuestion>> questions(@PathVariable Long projectId) { return ApiResponse.ok(service.questions(projectId)); }
    @PostMapping("/projects/{projectId}/golden-questions") @RequirePerm("pkc:evaluation:manage")
    public ApiResponse<GoldenQuestion> create(@PathVariable Long projectId, @Valid @RequestBody GoldenQuestionRequest request) { return ApiResponse.ok(service.create(projectId, request)); }
    @PutMapping("/golden-questions/{id}") @RequirePerm("pkc:evaluation:manage")
    public ApiResponse<GoldenQuestion> update(@PathVariable Long id, @Valid @RequestBody GoldenQuestionRequest request) { return ApiResponse.ok(service.update(id, request)); }
    @DeleteMapping("/golden-questions/{id}") @RequirePerm("pkc:evaluation:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(null); }
    @PostMapping("/projects/{projectId}/evaluation-runs") @RequirePerm("pkc:evaluation:run")
    public ApiResponse<EvaluationRun> start(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.ok(service.start(projectId, accessService.currentUser(request).getId()));
    }
    @GetMapping("/projects/{projectId}/evaluation-runs") @RequirePerm("pkc:project:view")
    public ApiResponse<List<EvaluationRun>> runs(@PathVariable Long projectId) { return ApiResponse.ok(service.runs(projectId)); }
    @GetMapping("/evaluation-runs/{id}") @RequirePerm("pkc:project:view")
    public ApiResponse<EvaluationRun> run(@PathVariable Long id) { return ApiResponse.ok(service.getRun(id)); }
}
