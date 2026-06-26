package com.projvault.pkc.project;

import com.projvault.common.BusinessException;
import com.projvault.pkc.artifact.ArtifactFolderRepository;
import com.projvault.pkc.artifact.GeneratedArtifactRepository;
import com.projvault.pkc.eval.EvaluationRunRepository;
import com.projvault.pkc.eval.GoldenQuestionRepository;
import com.projvault.pkc.file.ConfigItemRepository;
import com.projvault.pkc.file.DocChunkRepository;
import com.projvault.pkc.file.DocImageRepository;
import com.projvault.pkc.file.DocFamilyRepository;
import com.projvault.pkc.file.FileAssetRepository;
import com.projvault.pkc.file.GraphEdgeRepository;
import com.projvault.pkc.file.GraphNodeRepository;
import com.projvault.pkc.rag.AskHistoryRepository;
import com.projvault.pkc.scan.ScanChangeRepository;
import com.projvault.pkc.scan.ScanReportRepository;
import com.projvault.pkc.scan.ScanStatus;
import com.projvault.pkc.scan.ScanTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 项目档案服务（含级联删除）。
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ScanTaskRepository scanTaskRepository;
    private final FileAssetRepository fileAssetRepository;
    private final DocChunkRepository docChunkRepository;
    private final DocImageRepository docImageRepository;
    private final ConfigItemRepository configItemRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;
    private final DocFamilyRepository docFamilyRepository;
    private final ScanReportRepository scanReportRepository;
    private final ScanChangeRepository scanChangeRepository;
    private final AskHistoryRepository askHistoryRepository;
    private final GeneratedArtifactRepository generatedArtifactRepository;
    private final ArtifactFolderRepository artifactFolderRepository;
    private final GoldenQuestionRepository goldenQuestionRepository;
    private final EvaluationRunRepository evaluationRunRepository;

    public ProjectService(ProjectRepository projectRepository,
                          ScanTaskRepository scanTaskRepository,
                          FileAssetRepository fileAssetRepository,
                          DocChunkRepository docChunkRepository,
                          DocImageRepository docImageRepository,
                          ConfigItemRepository configItemRepository,
                          GraphNodeRepository graphNodeRepository,
                          GraphEdgeRepository graphEdgeRepository,
                          DocFamilyRepository docFamilyRepository,
                          ScanReportRepository scanReportRepository,
                          ScanChangeRepository scanChangeRepository,
                          AskHistoryRepository askHistoryRepository,
                          GeneratedArtifactRepository generatedArtifactRepository,
                          ArtifactFolderRepository artifactFolderRepository,
                          GoldenQuestionRepository goldenQuestionRepository,
                          EvaluationRunRepository evaluationRunRepository) {
        this.projectRepository = projectRepository;
        this.scanTaskRepository = scanTaskRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.docChunkRepository = docChunkRepository;
        this.docImageRepository = docImageRepository;
        this.configItemRepository = configItemRepository;
        this.graphNodeRepository = graphNodeRepository;
        this.graphEdgeRepository = graphEdgeRepository;
        this.docFamilyRepository = docFamilyRepository;
        this.scanReportRepository = scanReportRepository;
        this.scanChangeRepository = scanChangeRepository;
        this.askHistoryRepository = askHistoryRepository;
        this.generatedArtifactRepository = generatedArtifactRepository;
        this.artifactFolderRepository = artifactFolderRepository;
        this.goldenQuestionRepository = goldenQuestionRepository;
        this.evaluationRunRepository = evaluationRunRepository;
    }

    @Transactional
    public Project create(ProjectCreateRequest req, Long ownerUserId) {
        if (projectRepository.existsByCode(req.code())) {
            throw new BusinessException("项目编码已存在: " + req.code());
        }
        Project p = new Project();
        p.setCode(req.code());
        p.setName(req.name());
        p.setRootPath(validateRoot(req.rootPath()));
        p.setOwnerUserId(ownerUserId);
        return projectRepository.save(p);
    }

    @Transactional
    public Project update(Long id, ProjectUpdateRequest req) {
        Project p = getById(id);
        if (req.name() != null && !req.name().isBlank()) {
            p.setName(req.name());
        }
        if (req.rootPath() != null && !req.rootPath().isBlank()) {
            p.setRootPath(validateRoot(req.rootPath()));
        }
        if (req.status() != null && !req.status().isBlank()) {
            p.setStatus(req.status());
        }
        return projectRepository.save(p);
    }

    /**
     * 删除项目及其全部扫描派生数据；有进行中的扫描任务时拒绝删除。
     */
    @Transactional
    public void delete(Long id) {
        Project p = getById(id);
        boolean running = scanTaskRepository.existsByProjectIdAndStatusIn(
                id, List.of(ScanStatus.PENDING, ScanStatus.RUNNING));
        if (running) {
            throw new BusinessException("项目存在进行中的扫描任务，无法删除");
        }
        docChunkRepository.deleteByProjectId(id);
        docImageRepository.deleteByProjectId(id);
        configItemRepository.deleteByProjectId(id);
        fileAssetRepository.deleteByProjectId(id);
        graphEdgeRepository.deleteByProjectId(id);
        graphNodeRepository.deleteByProjectId(id);
        docFamilyRepository.deleteByProjectId(id);
        scanChangeRepository.deleteByProjectId(id);
        scanReportRepository.deleteByProjectId(id);
        askHistoryRepository.deleteByProjectId(id);
        evaluationRunRepository.deleteByProjectId(id);
        goldenQuestionRepository.deleteByProjectId(id);
        generatedArtifactRepository.deleteByProjectId(id);
        artifactFolderRepository.deleteByProjectId(id);
        scanTaskRepository.deleteByProjectId(id);
        projectRepository.delete(p);
    }

    public Project getById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "项目不存在: " + id));
    }

    public List<Project> listAll() {
        return projectRepository.findAll();
    }

    private String validateRoot(String rootPath) {
        Path root;
        try {
            root = Paths.get(rootPath).normalize();
        } catch (Exception e) {
            throw new BusinessException("资料根目录路径非法: " + rootPath);
        }
        if (!Files.isDirectory(root)) {
            throw new BusinessException("资料根目录不存在或不是目录: " + rootPath);
        }
        return root.toString();
    }
}
