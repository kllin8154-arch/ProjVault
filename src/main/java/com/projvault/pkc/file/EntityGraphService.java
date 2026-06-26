package com.projvault.pkc.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projvault.ai.GraphModelProvider;
import com.projvault.pkc.scan.ScanCancellation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EntityGraphService {

    private static final Logger log = LoggerFactory.getLogger(EntityGraphService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GraphModelProvider graphModelProvider;
    private final DocChunkRepository docChunkRepository;
    private final FileAssetRepository fileAssetRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;
    private final ScanCancellation scanCancellation;

    @Value("${projvault.graphrag.entity-extraction.enabled:false}")
    private boolean enabled;

    @Value("${projvault.graphrag.entity-extraction.max-chunks:80}")
    private int maxChunks;

    @Value("${projvault.ai.language:zh}")
    private String language;

    public EntityGraphService(GraphModelProvider graphModelProvider,
                              DocChunkRepository docChunkRepository,
                              FileAssetRepository fileAssetRepository,
                              GraphNodeRepository graphNodeRepository,
                              GraphEdgeRepository graphEdgeRepository,
                              ScanCancellation scanCancellation) {
        this.graphModelProvider = graphModelProvider;
        this.docChunkRepository = docChunkRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.graphNodeRepository = graphNodeRepository;
        this.graphEdgeRepository = graphEdgeRepository;
        this.scanCancellation = scanCancellation;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional
    public void removeEvidenceForFiles(Long projectId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        graphEdgeRepository.deleteEntityEvidenceByFileIds(projectId, fileIds);
    }

    @Transactional
    public int extractAndMerge(Long projectId, Long scanId) {
        List<DocChunk> chunks = docChunkRepository.findByProjectIdPaged(
                projectId, PageRequest.of(0, Math.max(1, maxChunks)));
        return extractAndMergeChunks(projectId, scanId, chunks);
    }

    @Transactional
    public int extractAndMergeChunks(Long projectId, Long scanId, List<DocChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }
        Set<Long> eligibleFileIds = fileAssetRepository.findByProjectIdAndDeletedFlagFalse(projectId).stream()
                .filter(ProjectRelevanceService::isKnowledgeEligible)
                .map(FileAsset::getId)
                .collect(Collectors.toSet());
        List<DocChunk> capped = chunks.stream()
                .filter(c -> eligibleFileIds.contains(c.getFileId()))
                .limit(Math.max(1, maxChunks))
                .toList();
        if (capped.isEmpty()) {
            return 0;
        }

        Extraction extraction = extract(capped, scanId);
        Map<String, Long> nameToId = upsertEntities(projectId, scanId, extraction);
        int relationCount = saveRelations(projectId, scanId, extraction.relations(), nameToId);
        int mentionCount = saveMentions(projectId, scanId, extraction.entityFiles(), nameToId);

        log.info("[entity-graph] project={} chunks={} entities={} relations={} mentions={}",
                projectId, capped.size(), nameToId.size(), relationCount, mentionCount);
        return nameToId.size() + relationCount + mentionCount;
    }

    @Transactional
    public int rebuildMentionsFromEvidence(Long projectId, Long scanId) {
        Map<String, Long> fileNodeByKey = new HashMap<>();
        for (GraphNode fn : graphNodeRepository.findByProjectIdAndNodeType(projectId, "FILE")) {
            fileNodeByKey.put(fn.getNodeKey(), fn.getId());
        }
        if (fileNodeByKey.isEmpty()) {
            return 0;
        }

        Map<Long, DocChunk> chunks = new HashMap<>();
        List<Long> evidenceIds = graphEdgeRepository.findByProjectIdAndEdgeType(projectId, "ENTITY_REL").stream()
                .map(GraphEdge::getEvidenceChunkId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        for (DocChunk chunk : docChunkRepository.findAllById(evidenceIds)) {
            chunks.put(chunk.getId(), chunk);
        }

        Set<String> seen = new HashSet<>();
        List<GraphEdge> mentions = new ArrayList<>();
        for (GraphEdge rel : graphEdgeRepository.findByProjectIdAndEdgeType(projectId, "ENTITY_REL")) {
            DocChunk chunk = chunks.get(rel.getEvidenceChunkId());
            if (chunk == null) {
                continue;
            }
            Long fileNodeId = fileNodeByKey.get("file:" + chunk.getFileId());
            if (fileNodeId == null) {
                continue;
            }
            String key = rel.getSourceId() + "->" + fileNodeId;
            if (seen.add(key)) {
                mentions.add(mention(projectId, scanId, rel.getSourceId(), fileNodeId));
            }
            key = rel.getTargetId() + "->" + fileNodeId;
            if (seen.add(key)) {
                mentions.add(mention(projectId, scanId, rel.getTargetId(), fileNodeId));
            }
        }
        graphEdgeRepository.saveAll(mentions);
        return mentions.size();
    }

    private Extraction extract(List<DocChunk> chunks, Long scanId) {
        Map<String, String> typeByName = new LinkedHashMap<>();
        Map<String, String> descByName = new HashMap<>();
        Map<String, LinkedHashSet<Long>> entityFiles = new HashMap<>();
        List<Rel> relations = new ArrayList<>();

        int processed = 0;
        for (DocChunk chunk : chunks) {
            if (scanCancellation.isCancelled(scanId)) {
                log.info("[entity-graph] 收到取消，停止抽取（已处理 {} 块）", processed);
                break;
            }
            try {
                JsonNode root = MAPPER.readTree(graphModelProvider.generateGraphFragment(chunk.getContent(), language));
                for (JsonNode n : root.path("nodes")) {
                    String name = n.path("name").asText("").strip();
                    if (name.isEmpty() || name.length() > 120) {
                        continue;
                    }
                    typeByName.putIfAbsent(name, trunc(n.path("type").asText(""), 64));
                    entityFiles.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(chunk.getFileId());
                    String desc = n.path("desc").asText("");
                    if (!desc.isBlank()) {
                        descByName.merge(name, desc, (a, b) -> a.length() >= b.length() ? a : b);
                    }
                }
                for (JsonNode e : root.path("edges")) {
                    String source = e.path("source").asText("").strip();
                    String target = e.path("target").asText("").strip();
                    if (source.isEmpty() || target.isEmpty() || source.equals(target)
                            || source.length() > 120 || target.length() > 120) {
                        continue;
                    }
                    relations.add(new Rel(source, target, e.path("type").asText(""),
                            e.path("desc").asText(""), e.path("weight").asDouble(0.5), chunk.getId()));
                    entityFiles.computeIfAbsent(source, k -> new LinkedHashSet<>()).add(chunk.getFileId());
                    entityFiles.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(chunk.getFileId());
                }
            } catch (Exception ex) {
                log.debug("[entity-graph] 片段解析失败 chunk={}: {}", chunk.getId(), ex.getMessage());
            }
            processed++;
        }
        for (Rel relation : relations) {
            typeByName.putIfAbsent(relation.source(), "");
            typeByName.putIfAbsent(relation.target(), "");
        }
        return new Extraction(typeByName, descByName, entityFiles, relations);
    }

    private Map<String, Long> upsertEntities(Long projectId, Long scanId, Extraction extraction) {
        Map<String, GraphNode> existing = graphNodeRepository.findByProjectIdAndNodeType(projectId, "ENTITY").stream()
                .collect(Collectors.toMap(GraphNode::getNodeKey, n -> n, (a, b) -> a));
        Map<String, Long> nameToId = new HashMap<>();
        for (Map.Entry<String, String> entry : extraction.typeByName().entrySet()) {
            String name = entry.getKey();
            String key = "ent:" + name;
            GraphNode node = existing.get(key);
            if (node == null) {
                node = new GraphNode();
                node.setProjectId(projectId);
                node.setNodeType("ENTITY");
                node.setNodeKey(key);
                node.setLabel(trunc(name, 255));
            }
            node.setScanId(scanId);
            if (node.getDocType() == null || node.getDocType().isBlank()) {
                node.setDocType(entry.getValue());
            }
            String desc = extraction.descByName().get(name);
            if (desc != null && (node.getSummary() == null || desc.length() > node.getSummary().length())) {
                node.setSummary(trunc(desc, 1000));
            }
            graphNodeRepository.save(node);
            nameToId.put(name, node.getId());
        }
        return nameToId;
    }

    private int saveRelations(Long projectId, Long scanId, List<Rel> relations, Map<String, Long> nameToId) {
        Set<String> seen = graphEdgeRepository.findByProjectIdAndEdgeType(projectId, "ENTITY_REL").stream()
                .map(e -> e.getSourceId() + "-" + e.getTargetId() + "-" + e.getEdgeType())
                .collect(Collectors.toCollection(HashSet::new));
        List<GraphEdge> edges = new ArrayList<>();
        for (Rel relation : relations) {
            Long sourceId = nameToId.get(relation.source());
            Long targetId = nameToId.get(relation.target());
            if (sourceId == null || targetId == null) {
                continue;
            }
            String key = sourceId + "-" + targetId + "-ENTITY_REL";
            if (!seen.add(key)) {
                continue;
            }
            GraphEdge edge = new GraphEdge();
            edge.setProjectId(projectId);
            edge.setScanId(scanId);
            edge.setSourceId(sourceId);
            edge.setTargetId(targetId);
            edge.setEdgeType("ENTITY_REL");
            edge.setDescription(trunc(relation.desc(), 1000));
            edge.setEvidenceChunkId(relation.chunkId());
            double weight = relation.weight();
            edge.setWeight(weight <= 0 ? 0.5 : Math.min(1.0, weight));
            edges.add(edge);
        }
        graphEdgeRepository.saveAll(edges);
        return edges.size();
    }

    private int saveMentions(Long projectId, Long scanId,
                             Map<String, LinkedHashSet<Long>> entityFiles,
                             Map<String, Long> nameToId) {
        Map<String, Long> fileNodeByKey = new HashMap<>();
        for (GraphNode fileNode : graphNodeRepository.findByProjectIdAndNodeType(projectId, "FILE")) {
            fileNodeByKey.put(fileNode.getNodeKey(), fileNode.getId());
        }
        Set<String> seen = new HashSet<>();
        List<GraphEdge> mentions = new ArrayList<>();
        for (Map.Entry<String, Long> entry : nameToId.entrySet()) {
            LinkedHashSet<Long> fileIds = entityFiles.get(entry.getKey());
            if (fileIds == null) {
                continue;
            }
            int linked = 0;
            for (Long fileId : fileIds) {
                if (linked >= 2) {
                    break;
                }
                Long fileNodeId = fileNodeByKey.get("file:" + fileId);
                if (fileNodeId == null) {
                    continue;
                }
                String key = entry.getValue() + "->" + fileNodeId;
                if (seen.add(key)) {
                    mentions.add(mention(projectId, scanId, entry.getValue(), fileNodeId));
                    linked++;
                }
            }
        }
        graphEdgeRepository.saveAll(mentions);
        return mentions.size();
    }

    private GraphEdge mention(Long projectId, Long scanId, Long entityNodeId, Long fileNodeId) {
        GraphEdge edge = new GraphEdge();
        edge.setProjectId(projectId);
        edge.setScanId(scanId);
        edge.setSourceId(entityNodeId);
        edge.setTargetId(fileNodeId);
        edge.setEdgeType("MENTIONED_IN");
        edge.setWeight(0.3);
        return edge;
    }

    private String trunc(String s, int n) {
        if (s == null) {
            return null;
        }
        return s.length() > n ? s.substring(0, n) : s;
    }

    private record Rel(String source, String target, String type, String desc, double weight, Long chunkId) {}

    private record Extraction(
            Map<String, String> typeByName,
            Map<String, String> descByName,
            Map<String, LinkedHashSet<Long>> entityFiles,
            List<Rel> relations) {}
}
