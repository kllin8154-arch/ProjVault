package com.projvault.pkc.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projvault.pkc.project.Project;
import com.projvault.pkc.scan.ScanTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * P6 图谱合并服务：从 FileAsset + ConfigItem 确定性地构建知识图谱。
 *
 * 节点：FILE（文件资产）、SERVICE（配置项归纳的服务端点）、DIRECTORY（目录分组）
 * 边  ：
 *   MENTIONS  — FILE → SERVICE（文件中包含该配置项）
 *   RELATED   — SERVICE ↔ SERVICE（共现关系）
 *   CONTAINS  — DIRECTORY → FILE（目录包含文件）
 *   IMPORTS   — FILE → FILE（Java import 依赖，仅限项目内文件）
 */
@Service
public class GraphMergeService {

    private static final Logger log = LoggerFactory.getLogger(GraphMergeService.class);

    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ConfigItemRepository configItemRepository;

    public GraphMergeService(GraphNodeRepository graphNodeRepository,
                              GraphEdgeRepository graphEdgeRepository,
                              FileAssetRepository fileAssetRepository,
                              ConfigItemRepository configItemRepository) {
        this.graphNodeRepository = graphNodeRepository;
        this.graphEdgeRepository = graphEdgeRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.configItemRepository = configItemRepository;
    }

    // ─── 工具方法 ────────────────────────────────────────────────────────────

    /** 从相对路径提取父目录（/ 分隔），根目录文件返回 "." */
    private String parentDir(String relPath) {
        int slash = relPath.lastIndexOf('/');
        if (slash <= 0) return ".";
        return relPath.substring(0, slash);
    }

    /** 取目录路径的最后一段用于 label */
    private String dirLabel(String dirPath) {
        if (".".equals(dirPath)) return "[root]";
        int slash = dirPath.lastIndexOf('/');
        return slash < 0 ? dirPath : dirPath.substring(slash + 1);
    }

    // ─── 主方法 ──────────────────────────────────────────────────────────────

    /**
     * 全量重建项目图谱，返回（节点数 + 边数）。
     */
    @Transactional
    public int merge(Project project, ScanTask task) {
        return merge(project, task, false);
    }

    @Transactional
    public int merge(Project project, ScanTask task, boolean preserveEntityGraph) {
        Long projectId = project.getId();
        Long scanId    = task.getId();
        String rootPath = project.getRootPath();

        // 1. 清空旧图（全量重建）
        if (preserveEntityGraph) {
            graphEdgeRepository.deleteStructuralByProjectId(projectId);
            graphNodeRepository.deleteStructuralByProjectId(projectId);
        } else {
            graphEdgeRepository.deleteByProjectId(projectId);
            graphNodeRepository.deleteByProjectId(projectId);
        }

        GraphNode projectNode = new GraphNode();
        projectNode.setProjectId(projectId);
        projectNode.setScanId(scanId);
        projectNode.setNodeType("PROJECT");
        projectNode.setNodeKey("project:" + projectId);
        projectNode.setLabel(project.getName());
        projectNode.setSummary(project.getRootPath());
        projectNode = graphNodeRepository.save(projectNode);

        // ── 2. 构建 FILE 节点 ─────────────────────────────────────────────────
        List<ConfigItem> allItems = configItemRepository.findByProjectIdOrderByKeyType(projectId);
        Set<Long> configSourceFileIds = allItems.stream()
                .filter(ci -> !"REJECTED".equals(ci.getReviewStatus()))
                .map(ConfigItem::getFileId)
                .collect(java.util.stream.Collectors.toSet());
        List<FileAsset> files = fileAssetRepository.findByProjectIdAndDeletedFlagFalse(projectId).stream()
                .filter(file -> ProjectRelevanceService.isKnowledgeEligible(file)
                        || configSourceFileIds.contains(file.getId()))
                .toList();
        Map<Long, GraphNode>   fileNodeById   = new HashMap<>();  // fileAsset.id → GraphNode
        Map<String, GraphNode> fileNodeByName = new HashMap<>();  // fileName     → GraphNode
        List<GraphNode> fileNodeInputs = new ArrayList<>();

        for (FileAsset fa : files) {
            GraphNode n = new GraphNode();
            n.setProjectId(projectId);
            n.setScanId(scanId);
            n.setNodeType("FILE");
            n.setNodeKey("file:" + fa.getId());
            n.setLabel(fa.getName());
            n.setDocType(fa.getDocType());
            n.setTags(fa.getTags());
            String s = fa.getSummary();
            n.setSummary(s != null && s.length() > 512 ? s.substring(0, 512) : s);
            fileNodeInputs.add(n);
        }
        List<GraphNode> savedFileNodes = graphNodeRepository.saveAll(fileNodeInputs);
        for (int i = 0; i < files.size(); i++) {
            fileNodeById.put(files.get(i).getId(), savedFileNodes.get(i));
            fileNodeByName.put(files.get(i).getName(), savedFileNodes.get(i));
        }

        // ── 3. 构建 DIRECTORY 节点 ────────────────────────────────────────────
        // 每个唯一父目录创建一个 DIRECTORY 节点
        Map<String, List<GraphNode>> dirToFileNodes = new LinkedHashMap<>();
        for (int i = 0; i < files.size(); i++) {
            String dir = parentDir(files.get(i).getRelPath());
            dirToFileNodes.computeIfAbsent(dir, k -> new ArrayList<>()).add(savedFileNodes.get(i));
        }

        Map<String, GraphNode> dirNodeMap = new LinkedHashMap<>();
        List<GraphNode> dirNodeInputs = new ArrayList<>();
        List<String>    dirPaths      = new ArrayList<>();
        for (String dirPath : dirToFileNodes.keySet()) {
            GraphNode dn = new GraphNode();
            dn.setProjectId(projectId);
            dn.setScanId(scanId);
            dn.setNodeType("DIRECTORY");
            dn.setNodeKey("dir:" + dirPath);
            dn.setLabel(dirLabel(dirPath));
            dn.setSummary(dirPath);   // 完整路径存入 summary 供 tooltip 显示
            dirNodeInputs.add(dn);
            dirPaths.add(dirPath);
        }
        List<GraphNode> savedDirNodes = graphNodeRepository.saveAll(dirNodeInputs);
        for (int i = 0; i < dirPaths.size(); i++) {
            dirNodeMap.put(dirPaths.get(i), savedDirNodes.get(i));
        }

        List<GraphEdge> projectEdges = new ArrayList<>();
        for (GraphNode dirNode : savedDirNodes) {
            GraphEdge e = new GraphEdge();
            e.setProjectId(projectId);
            e.setScanId(scanId);
            e.setSourceId(projectNode.getId());
            e.setTargetId(dirNode.getId());
            e.setEdgeType("PROJECT_CONTAINS");
            e.setDescription("PROJECT contains directory");
            e.setWeight(0.9);
            projectEdges.add(e);
        }
        graphEdgeRepository.saveAll(projectEdges);

        // ── 4. 构建 CONTAINS 边（DIRECTORY → FILE）────────────────────────────
        List<GraphEdge> containsEdges = new ArrayList<>();
        for (Map.Entry<String, List<GraphNode>> entry : dirToFileNodes.entrySet()) {
            GraphNode dirNode = dirNodeMap.get(entry.getKey());
            for (GraphNode fileNode : entry.getValue()) {
                GraphEdge e = new GraphEdge();
                e.setProjectId(projectId);
                e.setScanId(scanId);
                e.setSourceId(dirNode.getId());
                e.setTargetId(fileNode.getId());
                e.setEdgeType("CONTAINS");
                e.setDescription("目录包含文件");
                e.setWeight(0.5);
                containsEdges.add(e);
            }
        }
        graphEdgeRepository.saveAll(containsEdges);

        // ── 5. 构建 SERVICE 节点（按 keyValue 去重）──────────────────────────
        List<ConfigItem> items = allItems.stream()
                .filter(ci -> !"REJECTED".equals(ci.getReviewStatus()))
                .toList();
        Map<String, GraphNode> svcInputMap = new LinkedHashMap<>();
        for (ConfigItem ci : items) {
            String svcKey = "svc:" + ci.getKeyType() + ":" + ci.getKeyValue();
            if (!svcInputMap.containsKey(svcKey)) {
                GraphNode n = new GraphNode();
                n.setProjectId(projectId);
                n.setScanId(scanId);
                n.setNodeType("SERVICE");
                n.setNodeKey(svcKey);
                String lbl = ci.getKeyValue();
                n.setLabel(lbl.length() > 100 ? lbl.substring(0, 100) : lbl);
                n.setDocType(ci.getKeyType());
                svcInputMap.put(svcKey, n);
            }
        }
        List<GraphNode> savedSvcNodes = graphNodeRepository.saveAll(new ArrayList<>(svcInputMap.values()));
        Map<String, GraphNode> svcNodeMap = new HashMap<>();
        List<String> svcKeys = new ArrayList<>(svcInputMap.keySet());
        for (int i = 0; i < svcKeys.size(); i++) {
            svcNodeMap.put(svcKeys.get(i), savedSvcNodes.get(i));
        }

        // ── 6. 构建 MENTIONS 边（FILE → SERVICE）─────────────────────────────
        Set<String> edgeSeen = new HashSet<>();
        List<GraphEdge> mentionsEdges = new ArrayList<>();
        for (ConfigItem ci : items) {
            GraphNode fileNode = fileNodeById.get(ci.getFileId());
            String svcKey = "svc:" + ci.getKeyType() + ":" + ci.getKeyValue();
            GraphNode svcNode = svcNodeMap.get(svcKey);
            if (fileNode == null || svcNode == null) continue;
            String edgeKey = fileNode.getId() + "->" + svcNode.getId();
            if (!edgeSeen.add(edgeKey)) continue;
            GraphEdge edge = new GraphEdge();
            edge.setProjectId(projectId);
            edge.setScanId(scanId);
            edge.setSourceId(fileNode.getId());
            edge.setTargetId(svcNode.getId());
            edge.setEdgeType("MENTIONS");
            edge.setDescription(ci.getKeyType() + ": " + ci.getKeyValue());
            edge.setWeight(1.0);
            mentionsEdges.add(edge);
        }
        graphEdgeRepository.saveAll(mentionsEdges);

        // ── 7. 构建 RELATED 边（SERVICE 对在 2+ 不同文件中共现）────────────
        // 阈值设为 2：同一对 SERVICE 必须出现在至少 2 个不同文件中才建边。
        // 这避免了单文件（如 application.yml）中 N 个服务形成 K_N 完全图的"毛球"效果。
        Map<Long, Set<Long>> fileSvcMap = new HashMap<>();
        for (GraphEdge e : mentionsEdges) {
            fileSvcMap.computeIfAbsent(e.getSourceId(), k -> new HashSet<>()).add(e.getTargetId());
        }
        Map<String, Integer> pairFileCount = new HashMap<>();
        for (Set<Long> svcIds : fileSvcMap.values()) {
            if (svcIds.size() < 2) continue;
            List<Long> svcList = new ArrayList<>(svcIds);
            for (int i = 0; i < svcList.size(); i++) {
                for (int j = i + 1; j < svcList.size(); j++) {
                    long a = Math.min(svcList.get(i), svcList.get(j));
                    long b = Math.max(svcList.get(i), svcList.get(j));
                    pairFileCount.merge(a + "-" + b, 1, Integer::sum);
                }
            }
        }
        List<GraphEdge> relatedEdges = new ArrayList<>();
        for (Map.Entry<String, Integer> pe : pairFileCount.entrySet()) {
            if (pe.getValue() < 2) continue;   // 必须 2+ 文件共现
            String[] sp = pe.getKey().split("-");
            GraphEdge re = new GraphEdge();
            re.setProjectId(projectId);
            re.setScanId(scanId);
            re.setSourceId(Long.parseLong(sp[0]));
            re.setTargetId(Long.parseLong(sp[1]));
            re.setEdgeType("RELATED");
            re.setDescription("在 " + pe.getValue() + " 个文件中共同出现");
            re.setWeight(0.3);
            relatedEdges.add(re);
        }
        graphEdgeRepository.saveAll(relatedEdges);

        // ── 8. 构建 IMPORTS 边（Java 文件 import 项目内文件）─────────────────
        int importCount = buildImportsEdges(project, task, files, savedFileNodes,
                fileNodeByName, projectId, scanId, rootPath);

        log.info("[graph:{}] P6 完成 — PROJECT:1 FILE:{} DIR:{} SERVICE:{} PROJECT_CONTAINS:{} CONTAINS:{} MENTIONS:{} RELATED:{} IMPORTS:{}",
                task.getId(),
                savedFileNodes.size(), savedDirNodes.size(), savedSvcNodes.size(),
                projectEdges.size(), containsEdges.size(), mentionsEdges.size(), relatedEdges.size(), importCount);

        return 1 + savedFileNodes.size() + savedDirNodes.size() + savedSvcNodes.size()
                + projectEdges.size() + containsEdges.size() + mentionsEdges.size() + relatedEdges.size() + importCount;
    }

    /**
     * 解析 Java 文件的 import 语句，提取项目内部依赖并落库。
     * 匹配规则：import 某行的最后一段类名（去掉 .java 后）与项目内 FileAsset.name 完全相同。
     */
    private int buildImportsEdges(Project project, ScanTask task,
                                   List<FileAsset> files, List<GraphNode> savedFileNodes,
                                   Map<String, GraphNode> fileNodeByName,
                                   Long projectId, Long scanId, String rootPath) {
        Set<String> importSeen = new HashSet<>();
        List<GraphEdge> importEdges = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            FileAsset fa = files.get(i);
            if (!"java".equals(fa.getExt())) continue;
            GraphNode sourceNode = savedFileNodes.get(i);

            Path filePath = Paths.get(rootPath).resolve(fa.getRelPath().replace('/', java.io.File.separatorChar));
            if (!Files.exists(filePath)) continue;

            try (Stream<String> lines = Files.lines(filePath)) {
                lines.filter(line -> line.startsWith("import ") && !line.startsWith("import static "))
                    .forEach(line -> {
                        // "import com.projvault.pkc.file.GraphNode;" → "GraphNode"
                        String trimmed = line.trim();
                        if (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1);
                        int lastDot = trimmed.lastIndexOf('.');
                        if (lastDot < 0) return;
                        String className = trimmed.substring(lastDot + 1).trim();
                        if (className.isEmpty() || className.equals("*")) return;
                        String targetName = className + ".java";
                        GraphNode targetNode = fileNodeByName.get(targetName);
                        if (targetNode == null) return;
                        if (targetNode.getId().equals(sourceNode.getId())) return;
                        String edgeKey = sourceNode.getId() + "->" + targetNode.getId();
                        if (!importSeen.add(edgeKey)) return;
                        GraphEdge e = new GraphEdge();
                        e.setProjectId(projectId);
                        e.setScanId(scanId);
                        e.setSourceId(sourceNode.getId());
                        e.setTargetId(targetNode.getId());
                        e.setEdgeType("IMPORTS");
                        e.setDescription("Java import 依赖");
                        e.setWeight(0.8);
                        importEdges.add(e);
                    });
            } catch (IOException ex) {
                log.debug("[graph:{}] 跳过 {} IMPORTS 解析: {}", task.getId(), fa.getRelPath(), ex.getMessage());
            }
        }

        graphEdgeRepository.saveAll(importEdges);
        return importEdges.size();
    }
}
