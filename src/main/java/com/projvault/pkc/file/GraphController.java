package com.projvault.pkc.file;

import com.projvault.common.ApiResponse;
import com.projvault.pkc.project.Project;
import com.projvault.pkc.project.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱查询 API。
 * GET /api/pkc/projects/{projectId}/graph
 *   -> {nodes:[{id,label,nodeType,docType,tags,summary}], edges:[{source,target,edgeType,weight}]}
 * 节点 id 使用字符串形式，与 AntV G6 兼容。
 */
@RestController
@RequestMapping("/api/pkc/projects")
public class GraphController {

    private final ProjectRepository projectRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;

    public GraphController(ProjectRepository projectRepository,
                            GraphNodeRepository graphNodeRepository,
                            GraphEdgeRepository graphEdgeRepository) {
        this.projectRepository = projectRepository;
        this.graphNodeRepository = graphNodeRepository;
        this.graphEdgeRepository = graphEdgeRepository;
    }

    @GetMapping("/{projectId}/graph")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGraph(
            @PathVariable Long projectId) {

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(404, "项目不存在: " + projectId));
        }

        List<GraphNode> nodes = graphNodeRepository.findByProjectId(projectId);
        List<GraphEdge> edges = graphEdgeRepository.findByProjectId(projectId);

        List<Map<String, Object>> nodeList = new ArrayList<>(nodes.size());
        for (GraphNode n : nodes) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(n.getId()));
            m.put("nodeKey", n.getNodeKey());
            m.put("label", n.getLabel());
            m.put("nodeType", n.getNodeType());
            m.put("docType", n.getDocType());
            m.put("tags", n.getTags());
            m.put("summary", n.getSummary());
            nodeList.add(m);
        }

        List<Map<String, Object>> edgeList = new ArrayList<>(edges.size());
        for (GraphEdge e : edges) {
            Map<String, Object> m = new HashMap<>();
            m.put("source", String.valueOf(e.getSourceId()));
            m.put("target", String.valueOf(e.getTargetId()));
            m.put("edgeType", e.getEdgeType());
            m.put("description", e.getDescription());
            m.put("evidenceChunkId", e.getEvidenceChunkId());
            m.put("weight", e.getWeight());
            edgeList.add(m);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodeList);
        result.put("edges", edgeList);
        result.put("nodeCount", nodeList.size());
        result.put("edgeCount", edgeList.size());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
