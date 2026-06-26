package com.projvault.pkc.artifact;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pkc")
public class ArtifactController {

    private final ArtifactService artifactService;
    private final ArtifactPreviewService previewService;
    private final ArtifactRevisionService revisionService;
    private final ArtifactDiffService diffService;

    public ArtifactController(ArtifactService artifactService,
                              ArtifactPreviewService previewService,
                              ArtifactRevisionService revisionService,
                              ArtifactDiffService diffService) {
        this.artifactService = artifactService;
        this.previewService = previewService;
        this.revisionService = revisionService;
        this.diffService = diffService;
    }

    @GetMapping("/projects/{projectId}/artifacts/templates")
    @RequirePerm("pkc:project:view")
    public ApiResponse<List<Map<String, Object>>> templates(@PathVariable Long projectId) {
        return ApiResponse.ok(artifactService.templates());
    }

    @GetMapping("/projects/{projectId}/artifacts")
    @RequirePerm("pkc:project:view")
    public ApiResponse<List<GeneratedArtifactDTO>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(artifactService.list(projectId));
    }

    @GetMapping("/projects/{projectId}/artifacts/trash")
    @RequirePerm("pkc:artifact:manage")
    public ApiResponse<List<GeneratedArtifactDTO>> trash(@PathVariable Long projectId) {
        return ApiResponse.ok(artifactService.trash(projectId));
    }

    @GetMapping("/artifacts/{artifactId}")
    @RequirePerm("pkc:project:view")
    public ApiResponse<GeneratedArtifactDTO> get(@PathVariable Long artifactId) {
        return ApiResponse.ok(artifactService.get(artifactId));
    }

    @PostMapping("/projects/{projectId}/artifacts")
    @RequirePerm("pkc:artifact:generate")
    public ApiResponse<GeneratedArtifactDTO> generate(@PathVariable Long projectId,
                                                       @Valid @RequestBody GenerateArtifactRequest request) {
        return ApiResponse.ok(artifactService.generate(projectId, request));
    }

    @PutMapping("/artifacts/{artifactId}/review")
    @RequirePerm("pkc:artifact:review")
    public ApiResponse<GeneratedArtifactDTO> review(@PathVariable Long artifactId,
                                                     @Valid @RequestBody ReviewArtifactRequest request) {
        return ApiResponse.ok(artifactService.review(artifactId, request));
    }

    @GetMapping("/artifacts/{artifactId}/preview")
    @RequirePerm("pkc:project:view")
    public ApiResponse<ArtifactPreviewDTO> preview(@PathVariable Long artifactId) {
        return ApiResponse.ok(previewService.preview(artifactId));
    }

    @PutMapping("/artifacts/{artifactId}/previewed")
    @RequirePerm("pkc:artifact:review")
    public ApiResponse<GeneratedArtifactDTO> acknowledgePreview(@PathVariable Long artifactId) {
        return ApiResponse.ok(previewService.acknowledgePreview(artifactId));
    }

    @PostMapping("/artifacts/{artifactId}/revise")
    @RequirePerm("pkc:artifact:generate")
    public ApiResponse<GeneratedArtifactDTO> revise(@PathVariable Long artifactId,
                                                     @Valid @RequestBody ReviseArtifactRequest request) {
        return ApiResponse.ok(revisionService.revise(artifactId, request));
    }

    @PutMapping("/artifacts/{artifactId}/content")
    @RequirePerm("pkc:artifact:generate")
    public ApiResponse<GeneratedArtifactDTO> edit(@PathVariable Long artifactId,
                                                   @Valid @RequestBody EditArtifactRequest request) {
        return ApiResponse.ok(revisionService.edit(artifactId, request));
    }

    @PutMapping("/artifacts/{artifactId}")
    @RequirePerm("pkc:artifact:manage")
    public ApiResponse<GeneratedArtifactDTO> move(@PathVariable Long artifactId,
                                                   @Valid @RequestBody ArtifactMoveRequest request) {
        return ApiResponse.ok(artifactService.move(artifactId, request));
    }

    @DeleteMapping("/artifacts/{artifactId}")
    @RequirePerm("pkc:artifact:delete")
    public ApiResponse<GeneratedArtifactDTO> delete(@PathVariable Long artifactId) {
        return ApiResponse.ok(artifactService.delete(artifactId));
    }

    @PostMapping("/artifacts/{artifactId}/restore")
    @RequirePerm("pkc:artifact:delete")
    public ApiResponse<GeneratedArtifactDTO> restore(@PathVariable Long artifactId) {
        return ApiResponse.ok(artifactService.restore(artifactId));
    }

    @GetMapping("/artifacts/{artifactId}/diff")
    @RequirePerm("pkc:project:view")
    public ApiResponse<ArtifactDiffDTO> diff(@PathVariable Long artifactId,
                                             @RequestParam(required = false) Long baseId) {
        return ApiResponse.ok(diffService.diff(artifactId, baseId));
    }

    @GetMapping("/artifacts/{artifactId}/slides/{pageIndex}")
    @RequirePerm("pkc:project:view")
    public ResponseEntity<byte[]> slide(@PathVariable Long artifactId,
                                        @PathVariable int pageIndex) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(previewService.renderPptxPage(artifactId, pageIndex));
    }

    @GetMapping(value = "/artifacts/{artifactId}/pdf-preview", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePerm("pkc:project:view")
    public ResponseEntity<byte[]> pdfPreview(@PathVariable Long artifactId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline")
                .body(previewService.renderPdfPreview(artifactId));
    }

    @GetMapping("/artifacts/{artifactId}/inline")
    @RequirePerm("pkc:project:view")
    public ResponseEntity<byte[]> inline(@PathVariable Long artifactId) throws Exception {
        GeneratedArtifact artifact = artifactService.getEntity(artifactId);
        Path path = artifactService.resolveArtifactPath(artifact);
        ArtifactFormat format = ArtifactFormat.valueOf(artifact.getFormat());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.getMediaType()))
                .header("Content-Disposition", ContentDisposition.inline()
                        .filename(path.getFileName().toString(), StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff");
        if (format == ArtifactFormat.HTML) {
            response.header("Content-Security-Policy",
                    "default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:");
        }
        return response.body(Files.readAllBytes(path));
    }

    @GetMapping("/artifacts/{artifactId}/download")
    @RequirePerm("pkc:project:view")
    public ResponseEntity<byte[]> download(@PathVariable Long artifactId) throws Exception {
        GeneratedArtifact artifact = artifactService.getEntity(artifactId);
        Path path = artifactService.resolveArtifactPath(artifact);
        ArtifactFormat format = ArtifactFormat.valueOf(artifact.getFormat());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.getMediaType()))
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(path.getFileName().toString(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(Files.readAllBytes(path));
    }
}
