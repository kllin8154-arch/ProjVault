package com.projvault.pkc.artifact;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pkc")
public class ArtifactFolderController {

    private final ArtifactFolderService folderService;

    public ArtifactFolderController(ArtifactFolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping("/projects/{projectId}/artifact-folders")
    @RequirePerm("pkc:project:view")
    public ApiResponse<List<ArtifactFolderDTO>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(folderService.list(projectId));
    }

    @PostMapping("/projects/{projectId}/artifact-folders")
    @RequirePerm("pkc:artifact:manage")
    public ApiResponse<ArtifactFolderDTO> create(@PathVariable Long projectId,
                                                  @Valid @RequestBody ArtifactFolderRequest request) {
        return ApiResponse.ok(folderService.create(projectId, request));
    }

    @PutMapping("/artifact-folders/{id}")
    @RequirePerm("pkc:artifact:manage")
    public ApiResponse<ArtifactFolderDTO> update(@PathVariable Long id,
                                                  @Valid @RequestBody ArtifactFolderRequest request) {
        return ApiResponse.ok(folderService.update(id, request));
    }

    @DeleteMapping("/artifact-folders/{id}")
    @RequirePerm("pkc:artifact:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        folderService.delete(id);
        return ApiResponse.ok(null);
    }
}
