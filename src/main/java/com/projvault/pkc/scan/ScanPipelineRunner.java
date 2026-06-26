package com.projvault.pkc.scan;

import com.projvault.pkc.file.ChangeType;
import com.projvault.pkc.file.CommunityService;
import com.projvault.pkc.file.ConfigExtractService;
import com.projvault.pkc.file.ConfigItemRepository;
import com.projvault.pkc.file.DocumentParserService;
import com.projvault.pkc.file.DerivedDataCleanupService;
import com.projvault.pkc.file.EntityGraphService;
import com.projvault.pkc.file.FamilyClusterService;
import com.projvault.pkc.file.FileAssetRepository;
import com.projvault.pkc.file.FileChangeRecord;
import com.projvault.pkc.file.FingerprintService;
import com.projvault.pkc.file.GraphMergeService;
import com.projvault.pkc.file.ParseResult;
import com.projvault.pkc.file.SemanticService;
import com.projvault.pkc.project.Project;
import com.projvault.pkc.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ScanPipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(ScanPipelineRunner.class);

    private final ScanTaskRepository scanTaskRepository;
    private final ProjectRepository projectRepository;
    private final FingerprintService fingerprintService;
    private final DocumentParserService documentParserService;
    private final DerivedDataCleanupService derivedDataCleanupService;
    private final SemanticService semanticService;
    private final ConfigExtractService configExtractService;
    private final FileAssetRepository fileAssetRepository;
    private final ConfigItemRepository configItemRepository;
    private final GraphMergeService graphMergeService;
    private final FamilyClusterService familyClusterService;
    private final ReportGeneratorService reportGeneratorService;
    private final ScanChangeRepository scanChangeRepository;
    private final ScanCancellation scanCancellation;
    private final EntityGraphService entityGraphService;
    private final CommunityService communityService;

    @Value("${projvault.scan.large-file-threshold-bytes:268435456}")
    private long largeFileThresholdBytes;

    public ScanPipelineRunner(ScanTaskRepository scanTaskRepository,
                              ProjectRepository projectRepository,
                              FingerprintService fingerprintService,
                              DocumentParserService documentParserService,
                              DerivedDataCleanupService derivedDataCleanupService,
                              SemanticService semanticService,
                              ConfigExtractService configExtractService,
                              FileAssetRepository fileAssetRepository,
                              ConfigItemRepository configItemRepository,
                              GraphMergeService graphMergeService,
                              FamilyClusterService familyClusterService,
                              ReportGeneratorService reportGeneratorService,
                              ScanChangeRepository scanChangeRepository,
                              ScanCancellation scanCancellation,
                              EntityGraphService entityGraphService,
                              CommunityService communityService) {
        this.scanTaskRepository = scanTaskRepository;
        this.projectRepository = projectRepository;
        this.fingerprintService = fingerprintService;
        this.documentParserService = documentParserService;
        this.derivedDataCleanupService = derivedDataCleanupService;
        this.semanticService = semanticService;
        this.configExtractService = configExtractService;
        this.fileAssetRepository = fileAssetRepository;
        this.configItemRepository = configItemRepository;
        this.graphMergeService = graphMergeService;
        this.familyClusterService = familyClusterService;
        this.reportGeneratorService = reportGeneratorService;
        this.scanChangeRepository = scanChangeRepository;
        this.scanCancellation = scanCancellation;
        this.entityGraphService = entityGraphService;
        this.communityService = communityService;
    }

    @Async("scanExecutor")
    public void run(Long taskId) {
        ScanTask task = scanTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.error("scan task not found: {}", taskId);
            return;
        }
        Project project = projectRepository.findById(task.getProjectId()).orElse(null);
        if (project == null) {
            fail(task, "project not found: " + task.getProjectId());
            return;
        }

        task.setStatus(ScanStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        scanTaskRepository.save(task);

        try {
            List<Path> validPaths = phase1Enumerate(task, project);
            if (abortIfCancelled(task)) { return; }

            List<FileChangeRecord> changes = phase2Fingerprint(task, project, validPaths);
            if (abortIfCancelled(task)) { return; }

            if (task.getMode() == ScanMode.INCREMENTAL && task.getChangedFiles() == 0) {
                log.info("[scan:{}] incremental no file changes, skip heavy phases", task.getId());
                phase7Report(task);
                task.setStatus(ScanStatus.SUCCESS);
                return;
            }

            removeStaleEntityEvidence(task, project, changes);
            derivedDataCleanupService.cleanupDeleted(changes);
            if (abortIfCancelled(task)) { return; }

            List<ParseResult> parseResults = phase3Parse(task, project, changes);
            if (abortIfCancelled(task)) { return; }

            phase4Semantic(task, project, parseResults);
            if (abortIfCancelled(task)) { return; }

            phase5Extract(task, project, parseResults);
            if (abortIfCancelled(task)) { return; }

            phase6Merge(task, project, parseResults);
            if (abortIfCancelled(task)) { return; }

            phase7Report(task);
            task.setStatus(ScanStatus.SUCCESS);
        } catch (Exception e) {
            log.error("scan task {} failed", taskId, e);
            task.setStatus(ScanStatus.FAILED);
            task.setErrorMsg(truncate(e.getMessage()));
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            scanTaskRepository.save(task);
            scanCancellation.clear(taskId);
        }
    }

    private List<Path> phase1Enumerate(ScanTask task, Project project) throws IOException {
        enterPhase(task, ScanPhase.ENUMERATE);
        Path root = Paths.get(project.getRootPath());
        ScanIgnoreFilter filter = new ScanIgnoreFilter(root);
        List<Path> validPaths = new ArrayList<>();
        long totalBytes = 0;
        int largeFiles = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String rel = root.relativize(file).toString().replace(java.io.File.separator, "/");
                if (!filter.shouldIgnore(rel)) {
                    validPaths.add(file);
                    long size = Files.size(file);
                    totalBytes += size;
                    if (size >= largeFileThresholdBytes) largeFiles++;
                }
            }
        }
        task.setTotalFiles(validPaths.size());
        task.setTotalBytes(totalBytes);
        task.setLargeFileCount(largeFiles);
        scanTaskRepository.save(task);
        log.info("[scan:{}] P1 enumerate files={} bytes={} largeFiles={}",
                task.getId(), validPaths.size(), totalBytes, largeFiles);
        return validPaths;
    }

    private List<FileChangeRecord> phase2Fingerprint(ScanTask task, Project project, List<Path> validPaths) {
        enterPhase(task, ScanPhase.FINGERPRINT);
        Path root = Paths.get(project.getRootPath());
        List<FileChangeRecord> changes = fingerprintService.fingerprint(
                project.getId(), task.getId(), validPaths, root, task.getMode());

        Map<ChangeType, Long> summary = changes.stream()
                .collect(Collectors.groupingBy(FileChangeRecord::changeType, Collectors.counting()));
        int changed = (int) changes.stream()
                .filter(c -> c.changeType() != ChangeType.UNCHANGED)
                .count();
        task.setChangedFiles(changed);
        scanTaskRepository.save(task);
        persistChanges(task.getId(), project.getId(), changes);

        log.info("[scan:{}] P2 fingerprint added={} modified={} deleted={} renamed={} unchanged={}",
                task.getId(),
                summary.getOrDefault(ChangeType.ADDED, 0L),
                summary.getOrDefault(ChangeType.MODIFIED, 0L),
                summary.getOrDefault(ChangeType.DELETED, 0L),
                summary.getOrDefault(ChangeType.RENAMED, 0L),
                summary.getOrDefault(ChangeType.UNCHANGED, 0L));
        return changes;
    }

    private void persistChanges(Long scanId, Long projectId, List<FileChangeRecord> changes) {
        try {
            scanChangeRepository.deleteByScanId(scanId);
            List<ScanChange> records = new ArrayList<>();
            for (FileChangeRecord cr : changes) {
                if (cr.changeType() == ChangeType.UNCHANGED) {
                    continue;
                }
                ScanChange sc = new ScanChange();
                sc.setScanId(scanId);
                sc.setProjectId(projectId);
                sc.setRelPath(cr.asset().getRelPath());
                sc.setName(cr.asset().getName());
                sc.setChangeType(cr.changeType().name());
                sc.setOldRelPath(cr.oldRelPath());
                sc.setFileSize(cr.asset().getSize());
                sc.setSha256(cr.asset().getSha256());
                records.add(sc);
            }
            if (!records.isEmpty()) {
                scanChangeRepository.saveAll(records);
                log.info("[scan:{}] persisted changes={}", scanId, records.size());
            }
        } catch (Exception e) {
            log.warn("[scan:{}] persist changes failed: {}", scanId, e.getMessage());
        }
    }

    private void removeStaleEntityEvidence(ScanTask task, Project project, List<FileChangeRecord> changes) {
        if (task.getMode() != ScanMode.INCREMENTAL || !(task.isEntityExtraction() || entityGraphService.isEnabled())) {
            return;
        }
        List<Long> fileIds = changes.stream()
                .filter(c -> c.changeType() == ChangeType.MODIFIED || c.changeType() == ChangeType.DELETED)
                .map(c -> c.asset().getId())
                .distinct()
                .toList();
        entityGraphService.removeEvidenceForFiles(project.getId(), fileIds);
    }

    private List<ParseResult> phase3Parse(ScanTask task, Project project, List<FileChangeRecord> changes) {
        enterPhase(task, ScanPhase.PARSE);
        Path root = Paths.get(project.getRootPath());
        List<ParseResult> results = documentParserService.parseChanged(changes, root, task.getId());
        long ok = results.stream().filter(ParseResult::parseOk).count();
        long fail = results.stream().filter(r -> !r.parseOk()).count();
        log.info("[scan:{}] P3 parse ok={} failedOrSkipped={}", task.getId(), ok, fail);
        return results;
    }

    private void phase4Semantic(ScanTask task, Project project, List<ParseResult> parseResults) {
        enterPhase(task, ScanPhase.SEMANTIC);
        int count = semanticService.analyze(parseResults, task.getId(), project);
        int filled = semanticService.fillMissingRelevance(project);
        log.info("[scan:{}] P4 semantic files={} pathOnly={}", task.getId(), count, filled);
    }

    private void phase5Extract(ScanTask task, Project project, List<ParseResult> parseResults) {
        enterPhase(task, ScanPhase.EXTRACT);
        int count = configExtractService.extract(
                parseResults, project.getId(), task.getId(),
                fileAssetRepository, configItemRepository);
        log.info("[scan:{}] P5 config items={}", task.getId(), count);
    }

    private void phase6Merge(ScanTask task, Project project, List<ParseResult> parseResults) {
        enterPhase(task, ScanPhase.MERGE);
        boolean preserveEntityGraph = task.getMode() == ScanMode.INCREMENTAL;
        int graphTotal = graphMergeService.merge(project, task, preserveEntityGraph);
        log.info("[scan:{}] P6 structural graph total={}", task.getId(), graphTotal);

        int families = familyClusterService.clusterProject(project.getId(), task.getId());
        log.info("[scan:{}] P6 families={}", task.getId(), families);

        if (task.isEntityExtraction() || entityGraphService.isEnabled()) {
            int entityGraph = preserveEntityGraph
                    ? entityGraphService.extractAndMergeChunks(project.getId(), task.getId(),
                            parseResults.stream()
                                    .filter(ParseResult::parseOk)
                                    .flatMap(r -> r.chunks().stream())
                                    .toList())
                    : entityGraphService.extractAndMerge(project.getId(), task.getId());
            if (preserveEntityGraph) {
                int mentions = entityGraphService.rebuildMentionsFromEvidence(project.getId(), task.getId());
                log.info("[scan:{}] P6 entity mentions rebuilt={}", task.getId(), mentions);
            }
            log.info("[scan:{}] P6 entity graph total={}", task.getId(), entityGraph);
            if (!preserveEntityGraph || entityGraph > 0) {
                int communities = communityService.build(project.getId(), task.getId());
                log.info("[scan:{}] P6 communities={}", task.getId(), communities);
            } else {
                log.info("[scan:{}] P6 communities skipped, no incremental entity changes", task.getId());
            }
        }
    }

    private void phase7Report(ScanTask task) {
        enterPhase(task, ScanPhase.REPORT);
        reportGeneratorService.generate(task);
        log.info("[scan:{}] P7 report generated", task.getId());
    }

    private boolean abortIfCancelled(ScanTask task) {
        if (scanCancellation.isCancelled(task.getId())) {
            task.setStatus(ScanStatus.CANCELLED);
            log.info("[scan:{}] cancelled", task.getId());
            return true;
        }
        return false;
    }

    private void enterPhase(ScanTask task, ScanPhase phase) {
        task.setPhase(phase);
        scanTaskRepository.save(task);
    }

    private void fail(ScanTask task, String msg) {
        task.setStatus(ScanStatus.FAILED);
        task.setErrorMsg(truncate(msg));
        task.setFinishedAt(LocalDateTime.now());
        scanTaskRepository.save(task);
    }

    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}
