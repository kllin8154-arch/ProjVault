package com.projvault.pkc.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projvault.pkc.file.ConfigItem;
import com.projvault.pkc.file.ConfigItemRepository;
import com.projvault.pkc.file.FileAsset;
import com.projvault.pkc.file.FileAssetRepository;
import com.projvault.pkc.file.GraphEdge;
import com.projvault.pkc.file.GraphEdgeRepository;
import com.projvault.pkc.file.GraphNode;
import com.projvault.pkc.file.GraphNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * P7 报告生成服务：汇总文件、配置项、图谱统计和风险列表，序列化为 JSON 落库。
 */
@Service
public class ReportGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(ReportGeneratorService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ScanReportRepository scanReportRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ConfigItemRepository configItemRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;

    public ReportGeneratorService(ScanReportRepository scanReportRepository,
                                   FileAssetRepository fileAssetRepository,
                                   ConfigItemRepository configItemRepository,
                                   GraphNodeRepository graphNodeRepository,
                                   GraphEdgeRepository graphEdgeRepository) {
        this.scanReportRepository = scanReportRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.configItemRepository = configItemRepository;
        this.graphNodeRepository = graphNodeRepository;
        this.graphEdgeRepository = graphEdgeRepository;
    }

    @Transactional
    public ScanReport generate(ScanTask task) {
        Long projectId = task.getProjectId();
        Long scanId    = task.getId();

        // ── 文件统计 ──────────────────────────────────────────────────────────
        List<FileAsset> allFiles = fileAssetRepository.findByProjectIdAndDeletedFlagFalse(projectId);
        Map<String, Long> parseStats = allFiles.stream().collect(
                Collectors.groupingBy(
                        f -> f.getParseStatus() != null ? f.getParseStatus() : "PENDING",
                        Collectors.counting()));
        long newFiles = allFiles.stream()
                .filter(f -> scanId.equals(f.getFirstSeenScan()))
                .count();
        List<String> failedPaths = allFiles.stream()
                .filter(f -> "FAILED".equals(f.getParseStatus()))
                .map(FileAsset::getRelPath)
                .limit(20)
                .collect(Collectors.toList());

        // ── 配置项统计 ────────────────────────────────────────────────────────
        List<ConfigItem> allItems = configItemRepository.findByProjectIdOrderByKeyType(projectId);
        Map<String, Long> configByKeyType = allItems.stream().collect(
                Collectors.groupingBy(ConfigItem::getKeyType, Collectors.counting()));
        Map<String, Long> configByReviewStatus = allItems.stream().collect(
                Collectors.groupingBy(
                        c -> c.getReviewStatus() != null ? c.getReviewStatus() : "PENDING",
                        Collectors.counting()));

        // ── 图谱统计 ──────────────────────────────────────────────────────────
        List<GraphNode> allNodes = graphNodeRepository.findByProjectId(projectId);
        Map<String, Long> nodesByType = allNodes.stream().collect(
                Collectors.groupingBy(GraphNode::getNodeType, Collectors.counting()));
        List<GraphEdge> allEdges = graphEdgeRepository.findByProjectId(projectId);
        Map<String, Long> edgesByType = allEdges.stream().collect(
                Collectors.groupingBy(GraphEdge::getEdgeType, Collectors.counting()));

        // ── 风险列表 ──────────────────────────────────────────────────────────
        List<Map<String, Object>> risks = new ArrayList<>();
        if (!failedPaths.isEmpty()) {
            Map<String, Object> risk = new LinkedHashMap<>();
            risk.put("type", "PARSE_FAILED");
            risk.put("count", (long) failedPaths.size());
            risk.put("description", failedPaths.size() + " 个文件解析失败");
            risk.put("files", failedPaths);
            risks.add(risk);
        }
        long pendingConfigs = configByReviewStatus.getOrDefault("PENDING", 0L);
        if (pendingConfigs > 0) {
            Map<String, Object> risk = new LinkedHashMap<>();
            risk.put("type", "UNCONFIRMED_CONFIG");
            risk.put("count", pendingConfigs);
            risk.put("description", pendingConfigs + " 条配置项待人工确认");
            risks.add(risk);
        }

        // ── 构建报告 Map ──────────────────────────────────────────────────────
        // P7 在任务标记 SUCCESS 之前调用，status=RUNNING / finishedAt=null；按预期最终状态记录。
        String statusStr = (task.getStatus() == null || ScanStatus.RUNNING == task.getStatus())
                ? "SUCCESS" : task.getStatus().name();
        java.time.LocalDateTime reportFinishedAt =
                task.getFinishedAt() != null ? task.getFinishedAt() : java.time.LocalDateTime.now();
        long durationMs = task.getStartedAt() != null
                ? Duration.between(task.getStartedAt(), reportFinishedAt).toMillis()
                : -1;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scanId", scanId);
        report.put("projectId", projectId);
        report.put("mode", task.getMode() != null ? task.getMode().name() : "INCREMENTAL");
        report.put("status", statusStr);
        report.put("startedAt", task.getStartedAt() != null ? task.getStartedAt().toString() : null);
        report.put("finishedAt", reportFinishedAt.toString());
        report.put("durationMs", durationMs);

        Map<String, Object> filesMap = new LinkedHashMap<>();
        filesMap.put("total", task.getTotalFiles());
        filesMap.put("changed", task.getChangedFiles());
        filesMap.put("totalBytes", task.getTotalBytes());
        filesMap.put("largeFileCount", task.getLargeFileCount());
        filesMap.put("newInThisScan", newFiles);
        filesMap.put("heavyPhasesSkipped",
                task.getMode() == ScanMode.INCREMENTAL && task.getChangedFiles() == 0);
        filesMap.put("parseStats", parseStats);
        report.put("files", filesMap);

        Map<String, Object> configMap = new LinkedHashMap<>();
        configMap.put("total", (long) allItems.size());
        configMap.put("byKeyType", configByKeyType);
        configMap.put("byReviewStatus", configByReviewStatus);
        report.put("configItems", configMap);

        Map<String, Object> graphMap = new LinkedHashMap<>();
        graphMap.put("totalNodes", (long) allNodes.size());
        graphMap.put("byNodeType", nodesByType);
        graphMap.put("totalEdges", (long) allEdges.size());
        graphMap.put("byEdgeType", edgesByType);
        report.put("graph", graphMap);

        report.put("risks", risks);

        // ── 序列化并落库 ──────────────────────────────────────────────────────
        String json;
        try {
            json = MAPPER.writeValueAsString(report);
        } catch (Exception e) {
            log.error("[report:{}] JSON 序列化失败", scanId, e);
            json = "{\"error\":\"序列化失败\"}";
        }
        ScanReport entity = scanReportRepository.findByScanId(scanId).orElse(new ScanReport());
        entity.setScanId(scanId);
        entity.setProjectId(projectId);
        entity.setReportJson(json);
        ScanReport saved = scanReportRepository.save(entity);
        log.info("[report:{}] P7 完成 — 文件:{} 配置项:{} 节点:{} 边:{} 风险:{}",
                scanId, task.getTotalFiles(), allItems.size(), allNodes.size(), allEdges.size(), risks.size());
        return saved;
    }
}
