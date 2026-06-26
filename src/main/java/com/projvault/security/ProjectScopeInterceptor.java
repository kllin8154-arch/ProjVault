package com.projvault.security;

import com.projvault.pkc.artifact.ArtifactFolderRepository;
import com.projvault.pkc.artifact.GeneratedArtifactRepository;
import com.projvault.pkc.eval.EvaluationRunRepository;
import com.projvault.pkc.eval.GoldenQuestionRepository;
import com.projvault.pkc.file.ConfigItemRepository;
import com.projvault.pkc.file.DocChunkRepository;
import com.projvault.pkc.file.DocFamilyRepository;
import com.projvault.pkc.file.DocImageRepository;
import com.projvault.pkc.file.FileAssetRepository;
import com.projvault.pkc.scan.ScanTaskRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProjectScopeInterceptor implements HandlerInterceptor {
    private static final Pattern PROJECT_PATH = Pattern.compile("^/api/pkc/projects/(\\d+)(?:/|$)");

    private final ProjectAccessService accessService;
    private final ScanTaskRepository scans;
    private final FileAssetRepository files;
    private final DocChunkRepository chunks;
    private final DocImageRepository images;
    private final ConfigItemRepository configs;
    private final DocFamilyRepository families;
    private final GeneratedArtifactRepository artifacts;
    private final ArtifactFolderRepository folders;
    private final GoldenQuestionRepository questions;
    private final EvaluationRunRepository evaluations;

    public ProjectScopeInterceptor(ProjectAccessService accessService,
                                   ScanTaskRepository scans,
                                   FileAssetRepository files,
                                   DocChunkRepository chunks,
                                   DocImageRepository images,
                                   ConfigItemRepository configs,
                                   DocFamilyRepository families,
                                   GeneratedArtifactRepository artifacts,
                                   ArtifactFolderRepository folders,
                                   GoldenQuestionRepository questions,
                                   EvaluationRunRepository evaluations) {
        this.accessService = accessService;
        this.scans = scans;
        this.files = files;
        this.chunks = chunks;
        this.images = images;
        this.configs = configs;
        this.families = families;
        this.artifacts = artifacts;
        this.folders = folders;
        this.questions = questions;
        this.evaluations = evaluations;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        Long projectId = resolveProjectId(request.getRequestURI());
        if (projectId != null) {
            accessService.requireProject(request, projectId);
        }
        return true;
    }

    private Long resolveProjectId(String path) {
        Matcher project = PROJECT_PATH.matcher(path);
        if (project.find()) {
            return Long.valueOf(project.group(1));
        }
        Long id = pathId(path, "/api/pkc/scans/");
        if (id != null) return scans.findById(id).map(task -> task.getProjectId()).orElse(null);
        id = pathId(path, "/api/pkc/files/");
        if (id != null) return files.findById(id).map(file -> file.getProjectId()).orElse(null);
        id = pathId(path, "/api/pkc/chunks/");
        if (id != null) return chunks.findById(id).flatMap(chunk -> files.findById(chunk.getFileId()))
                .map(file -> file.getProjectId()).orElse(null);
        id = pathId(path, "/api/pkc/doc-images/");
        if (id != null) return images.findById(id).map(image -> image.getProjectId()).orElse(null);
        id = pathId(path, "/api/pkc/config-items/");
        if (id != null) return configs.findById(id).map(item -> item.getProjectId()).orElse(null);
        id = pathId(path, "/api/pkc/doc-families/");
        if (id != null) return families.findById(id).map(family -> family.getProjectId()).orElse(null);
        id = pathId(path, "/api/pkc/artifacts/");
        if (id != null) return artifacts.findById(id).map(artifact -> artifact.getProjectId()).orElse(null);
        id = pathId(path, "/api/pkc/artifact-folders/");
        if (id != null) return folders.findById(id).map(folder -> folder.getProjectId()).orElse(null);
        id = pathId(path, "/api/pkc/golden-questions/");
        if (id != null) return questions.findById(id).map(question -> question.getProjectId()).orElse(null);
        id = pathId(path, "/api/pkc/evaluation-runs/");
        if (id != null) return evaluations.findById(id).map(run -> run.getProjectId()).orElse(null);
        return null;
    }

    private Long pathId(String path, String prefix) {
        if (!path.startsWith(prefix)) return null;
        String tail = path.substring(prefix.length());
        int slash = tail.indexOf('/');
        String raw = slash >= 0 ? tail.substring(0, slash) : tail;
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
