package com.projvault.pkc.artifact;

import com.projvault.common.BusinessException;
import com.projvault.pkc.project.Project;
import com.projvault.pkc.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ArtifactFolderService {

    private final ArtifactFolderRepository folderRepository;
    private final GeneratedArtifactRepository artifactRepository;
    private final ProjectRepository projectRepository;

    public ArtifactFolderService(ArtifactFolderRepository folderRepository,
                                 GeneratedArtifactRepository artifactRepository,
                                 ProjectRepository projectRepository) {
        this.folderRepository = folderRepository;
        this.artifactRepository = artifactRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public List<ArtifactFolderDTO> list(Long projectId) {
        ensureDefaultFolder(projectId);
        return folderRepository.findByProjectIdOrderByDefaultFolderDescNameAsc(projectId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ArtifactFolderDTO create(Long projectId, ArtifactFolderRequest request) {
        Project project = getProject(projectId);
        String relativePath = normalizeFolderPath(request.getRelativePath());
        if (folderRepository.findByProjectIdAndRelativePath(projectId, relativePath).isPresent()) {
            throw new BusinessException(409, "该交付物目录已登记");
        }
        Path target = resolve(project, relativePath);
        try {
            Files.createDirectories(target);
        } catch (Exception e) {
            throw new BusinessException(500, "创建交付物目录失败: " + e.getMessage());
        }
        ArtifactFolder folder = new ArtifactFolder();
        folder.setProjectId(projectId);
        apply(folder, request, relativePath);
        if (folder.isDefaultFolder()) {
            clearDefault(projectId, null);
        }
        return toDto(folderRepository.save(folder));
    }

    @Transactional
    public ArtifactFolderDTO update(Long id, ArtifactFolderRequest request) {
        ArtifactFolder folder = get(id);
        Project project = getProject(folder.getProjectId());
        String oldPath = folder.getRelativePath();
        String newPath = normalizeFolderPath(request.getRelativePath());
        if (ArtifactService.REVIEW_ROOT.equals(oldPath) && !oldPath.equals(newPath)) {
            throw new BusinessException(409, "系统待审查根位置不能移动，只能修改名称和说明");
        }
        if (!oldPath.equals(newPath)) {
            folderRepository.findByProjectIdAndRelativePath(folder.getProjectId(), newPath)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> { throw new BusinessException(409, "目标目录已登记"); });
            moveDirectory(project, oldPath, newPath);
            rewriteManagedPaths(folder.getProjectId(), oldPath, newPath);
        }
        apply(folder, request, newPath);
        if (folder.isDefaultFolder()) {
            clearDefault(folder.getProjectId(), folder.getId());
        }
        return toDto(folderRepository.save(folder));
    }

    @Transactional
    public void delete(Long id) {
        ArtifactFolder folder = get(id);
        if (ArtifactService.REVIEW_ROOT.equals(folder.getRelativePath())) {
            throw new BusinessException(409, "系统默认待审查目录不能删除");
        }
        String prefix = folder.getRelativePath() + "/";
        if (artifactRepository.countByProjectIdAndRelativePathStartingWithAndDeletedAtIsNull(
                folder.getProjectId(), prefix) > 0) {
            throw new BusinessException(409, "目录中仍有交付物，请先移动或放入回收站");
        }
        List<ArtifactFolder> descendants = folderRepository
                .findByProjectIdAndRelativePathStartingWith(folder.getProjectId(), prefix);
        if (!descendants.isEmpty()) {
            throw new BusinessException(409, "目录中仍有子目录，不能删除");
        }
        Path target = resolve(getProject(folder.getProjectId()), folder.getRelativePath());
        try {
            if (Files.isDirectory(target)) {
                try (var children = Files.list(target)) {
                    if (children.findAny().isPresent()) {
                        throw new BusinessException(409, "目录中仍有未登记文件，不能删除");
                    }
                }
                Files.delete(target);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "删除交付物目录失败: " + e.getMessage());
        }
        folderRepository.delete(folder);
    }

    public ArtifactFolder get(Long id) {
        return folderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "交付物目录不存在: " + id));
    }

    public String folderPath(Long projectId, Long folderId) {
        if (folderId == null) {
            return ensureDefaultFolder(projectId).getRelativePath();
        }
        ArtifactFolder folder = get(folderId);
        if (!folder.getProjectId().equals(projectId)) {
            throw new BusinessException(403, "交付物目录不属于当前项目");
        }
        return folder.getRelativePath();
    }

    private ArtifactFolder ensureDefaultFolder(Long projectId) {
        getProject(projectId);
        return folderRepository.findByProjectIdAndRelativePath(projectId, ArtifactService.REVIEW_ROOT)
                .orElseGet(() -> {
                    ArtifactFolder folder = new ArtifactFolder();
                    folder.setProjectId(projectId);
                    folder.setName("待审查");
                    folder.setRelativePath(ArtifactService.REVIEW_ROOT);
                    folder.setDescription("AI 生成草稿的默认审查目录");
                    folder.setDefaultFolder(true);
                    return folderRepository.save(folder);
                });
    }

    private void moveDirectory(Project project, String oldPath, String newPath) {
        Path source = resolve(project, oldPath);
        Path target = resolve(project, newPath);
        if (!Files.exists(source)) {
            try {
                Files.createDirectories(target);
                return;
            } catch (Exception e) {
                throw new BusinessException(500, "创建目标目录失败: " + e.getMessage());
            }
        }
        if (Files.exists(target)) {
            throw new BusinessException(409, "目标物理目录已存在");
        }
        try {
            Files.createDirectories(target.getParent());
            move(source, target);
        } catch (Exception e) {
            throw new BusinessException(500, "移动交付物目录失败: " + e.getMessage());
        }
    }

    private void rewriteManagedPaths(Long projectId, String oldPath, String newPath) {
        String prefix = oldPath + "/";
        List<GeneratedArtifact> artifacts = artifactRepository
                .findByProjectIdAndRelativePathStartingWithAndDeletedAtIsNull(projectId, prefix);
        for (GeneratedArtifact artifact : artifacts) {
            artifact.setRelativePath(newPath + artifact.getRelativePath().substring(oldPath.length()));
        }
        artifactRepository.saveAll(artifacts);

        List<ArtifactFolder> folders = folderRepository
                .findByProjectIdAndRelativePathStartingWith(projectId, prefix);
        for (ArtifactFolder nested : folders) {
            nested.setRelativePath(newPath + nested.getRelativePath().substring(oldPath.length()));
            nested.setUpdatedAt(LocalDateTime.now());
        }
        folderRepository.saveAll(folders);
    }

    private void clearDefault(Long projectId, Long exceptId) {
        for (ArtifactFolder candidate : folderRepository.findByProjectIdOrderByDefaultFolderDescNameAsc(projectId)) {
            if (candidate.isDefaultFolder() && !candidate.getId().equals(exceptId)) {
                candidate.setDefaultFolder(false);
                folderRepository.save(candidate);
            }
        }
    }

    private void apply(ArtifactFolder folder, ArtifactFolderRequest request, String relativePath) {
        folder.setName(request.getName().strip());
        folder.setRelativePath(relativePath);
        folder.setDescription(request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().strip());
        folder.setDefaultFolder(request.isDefaultFolder());
        folder.setUpdatedAt(LocalDateTime.now());
    }

    private String normalizeFolderPath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("交付物目录不能为空");
        }
        Path relative = Path.of(raw.replace('\\', '/')).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new BusinessException("交付物目录必须是项目内相对路径");
        }
        String normalized = relative.toString().replace('\\', '/');
        if (!normalized.equals(ArtifactService.REVIEW_ROOT)
                && !normalized.startsWith(ArtifactService.REVIEW_ROOT + "/")) {
            throw new BusinessException("交付物目录必须位于 " + ArtifactService.REVIEW_ROOT + " 下");
        }
        return normalized;
    }

    private Path resolve(Project project, String relativePath) {
        Path root = Path.of(project.getRootPath()).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("交付物目录越过项目根目录");
        }
        return target;
    }

    private Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在: " + projectId));
    }

    private void move(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private ArtifactFolderDTO toDto(ArtifactFolder folder) {
        long count = artifactRepository.countByProjectIdAndRelativePathStartingWithAndDeletedAtIsNull(
                folder.getProjectId(), folder.getRelativePath() + "/");
        return new ArtifactFolderDTO(folder.getId(), folder.getProjectId(), folder.getName(),
                folder.getRelativePath(), folder.getDescription(), folder.isDefaultFolder(), count,
                folder.getCreatedAt(), folder.getUpdatedAt());
    }
}
