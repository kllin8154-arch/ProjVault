package com.projvault.pkc.file;

import com.projvault.ai.SummaryModelProvider;
import com.projvault.pkc.scan.ScanCancellation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GraphRAG Global 底座：在 ENTITY 子图上做社区检测 + 社区摘要。
 *
 * 社区检测用标签传播算法（LPA，零依赖、O(边)）：每节点初始标签为自身，
 * 迭代采用邻居中最频繁的标签，收敛即得社区划分。
 * 每个社区汇集成员实体+内部关系，调 SummaryModelProvider 生成主题摘要落库。
 */
@Service
public class CommunityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityService.class);

    private static final int MAX_ITERS = 12;
    private static final int MIN_COMMUNITY_SIZE = 2;   // 单点社区不单独摘要
    private static final int MAX_CTX_ENTITIES = 30;    // 摘要上下文实体上限

    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;
    private final GraphCommunityRepository graphCommunityRepository;
    private final SummaryModelProvider summaryModelProvider;
    private final ScanCancellation scanCancellation;

    @Value("${projvault.ai.language:zh}")
    private String language;

    public CommunityService(GraphNodeRepository graphNodeRepository,
                            GraphEdgeRepository graphEdgeRepository,
                            GraphCommunityRepository graphCommunityRepository,
                            SummaryModelProvider summaryModelProvider,
                            ScanCancellation scanCancellation) {
        this.graphNodeRepository = graphNodeRepository;
        this.graphEdgeRepository = graphEdgeRepository;
        this.graphCommunityRepository = graphCommunityRepository;
        this.summaryModelProvider = summaryModelProvider;
        this.scanCancellation = scanCancellation;
    }

    @Transactional
    public int build(Long projectId, Long scanId) {
        graphCommunityRepository.deleteByProjectId(projectId);

        List<GraphNode> entities = graphNodeRepository.findByProjectIdAndNodeType(projectId, "ENTITY");
        if (entities.size() < MIN_COMMUNITY_SIZE) {
            return 0;
        }
        Map<Long, GraphNode> nodeById = new HashMap<>();
        for (GraphNode n : entities) {
            nodeById.put(n.getId(), n);
        }
        List<GraphEdge> edges = graphEdgeRepository.findByProjectIdAndEdgeType(projectId, "ENTITY_REL");

        // 邻接表（无向）+ 度数
        Map<Long, List<Long>> adj = new HashMap<>();
        Map<Long, Integer> degree = new HashMap<>();
        for (GraphNode n : entities) {
            adj.put(n.getId(), new ArrayList<>());
            degree.put(n.getId(), 0);
        }
        for (GraphEdge e : edges) {
            if (adj.containsKey(e.getSourceId()) && adj.containsKey(e.getTargetId())) {
                adj.get(e.getSourceId()).add(e.getTargetId());
                adj.get(e.getTargetId()).add(e.getSourceId());
                degree.merge(e.getSourceId(), 1, Integer::sum);
                degree.merge(e.getTargetId(), 1, Integer::sum);
            }
        }

        Map<Long, Long> label = labelPropagation(entities, adj);

        // 按标签分组
        Map<Long, List<Long>> groups = new LinkedHashMap<>();
        for (GraphNode n : entities) {
            groups.computeIfAbsent(label.get(n.getId()), k -> new ArrayList<>()).add(n.getId());
        }
        // 大社区在前
        List<List<Long>> comms = new ArrayList<>(groups.values());
        comms.sort(Comparator.comparingInt((List<Long> c) -> c.size()).reversed());

        int no = 0;
        int saved = 0;
        for (List<Long> members : comms) {
            if (members.size() < MIN_COMMUNITY_SIZE) {
                continue;
            }
            if (scanCancellation.isCancelled(scanId)) {
                log.info("[community] 收到取消，停止社区摘要（已 {} 个）", saved);
                break;
            }
            members.sort(Comparator.comparingInt((Long id) -> degree.getOrDefault(id, 0)).reversed());
            String summary = summarizeCommunity(members, nodeById, adj);
            String memberNames = membersLabel(members, nodeById);

            GraphCommunity gc = new GraphCommunity();
            gc.setProjectId(projectId);
            gc.setScanId(scanId);
            gc.setCommunityNo(no++);
            gc.setSize(members.size());
            gc.setMembers(trunc(memberNames, 1024));
            gc.setSummary(summary);
            graphCommunityRepository.save(gc);
            saved++;
        }
        log.info("[community] project={} 实体={} 社区={}", projectId, entities.size(), saved);
        return saved;
    }

    private Map<Long, Long> labelPropagation(List<GraphNode> entities, Map<Long, List<Long>> adj) {
        Map<Long, Long> label = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (GraphNode n : entities) {
            label.put(n.getId(), n.getId());
            ids.add(n.getId());
        }
        ids.sort(Comparator.naturalOrder());   // 确定性顺序
        for (int it = 0; it < MAX_ITERS; it++) {
            boolean changed = false;
            for (Long id : ids) {
                List<Long> nbrs = adj.get(id);
                if (nbrs == null || nbrs.isEmpty()) {
                    continue;
                }
                Map<Long, Integer> freq = new HashMap<>();
                for (Long nb : nbrs) {
                    freq.merge(label.get(nb), 1, Integer::sum);
                }
                // 取频次最高；并列取标签值最小（确定性）
                long best = label.get(id);
                int bestCount = -1;
                for (Map.Entry<Long, Integer> en : freq.entrySet()) {
                    if (en.getValue() > bestCount
                            || (en.getValue() == bestCount && en.getKey() < best)) {
                        best = en.getKey();
                        bestCount = en.getValue();
                    }
                }
                if (best != label.get(id)) {
                    label.put(id, best);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return label;
    }

    private String summarizeCommunity(List<Long> members, Map<Long, GraphNode> nodeById,
                                      Map<Long, List<Long>> adj) {
        StringBuilder ctx = new StringBuilder("实体：\n");
        int cap = Math.min(members.size(), MAX_CTX_ENTITIES);
        for (int i = 0; i < cap; i++) {
            GraphNode n = nodeById.get(members.get(i));
            if (n == null) {
                continue;
            }
            ctx.append("- ").append(n.getLabel());
            if (n.getDocType() != null && !n.getDocType().isBlank()) {
                ctx.append("（").append(n.getDocType()).append("）");
            }
            if (n.getSummary() != null && !n.getSummary().isBlank()) {
                ctx.append("：").append(n.getSummary());
            }
            ctx.append('\n');
        }
        try {
            String s = summaryModelProvider.summarize("实体社区", ctx.toString(), language);
            if (s != null && !s.isBlank()) {
                return s;
            }
        } catch (Exception e) {
            log.warn("[community] 摘要失败: {}", e.getMessage());
        }
        // 回退：结构化拼装
        return "核心实体：" + membersLabel(members.subList(0, Math.min(members.size(), 6)), nodeById);
    }

    private String membersLabel(List<Long> members, Map<Long, GraphNode> nodeById) {
        List<String> names = new ArrayList<>();
        for (Long id : members) {
            GraphNode n = nodeById.get(id);
            if (n != null && n.getLabel() != null) {
                names.add(n.getLabel());
            }
        }
        return String.join("、", names);
    }

    private String trunc(String s, int n) {
        if (s == null) {
            return null;
        }
        return s.length() > n ? s.substring(0, n) : s;
    }
}
