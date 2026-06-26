package com.projvault.pkc.rag;

import com.projvault.pkc.file.DocChunk;
import com.projvault.pkc.file.DocChunkRepository;
import com.projvault.pkc.file.GraphEdge;
import com.projvault.pkc.file.GraphEdgeRepository;
import com.projvault.pkc.file.GraphNode;
import com.projvault.pkc.file.GraphNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LocalSearchExpander {

    private static final Logger log = LoggerFactory.getLogger(LocalSearchExpander.class);

    private static final int MAX_SEEDS = 4;
    private static final int MAX_RELATIONS = 18;
    private static final int MAX_NEIGHBOR_LABELS = 12;
    private static final int PER_LABEL_CHUNKS = 2;

    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;
    private final DocChunkRepository docChunkRepository;

    public LocalSearchExpander(GraphNodeRepository graphNodeRepository,
                               GraphEdgeRepository graphEdgeRepository,
                               DocChunkRepository docChunkRepository) {
        this.graphNodeRepository = graphNodeRepository;
        this.graphEdgeRepository = graphEdgeRepository;
        this.docChunkRepository = docChunkRepository;
    }

    public LocalSearchResult expand(Long projectId, String question,
                                    List<DocChunk> seedChunks, int extraK) {
        List<GraphNode> entities = graphNodeRepository.findByProjectIdAndNodeType(projectId, "ENTITY");
        if (entities.isEmpty()) {
            return LocalSearchResult.empty();
        }

        Map<Long, GraphNode> nodeById = new HashMap<>();
        StringBuilder seedText = new StringBuilder(question == null ? "" : question);
        for (DocChunk c : seedChunks) {
            if (c.getContent() != null) {
                seedText.append('\n').append(c.getContent());
            }
        }

        List<SeedCandidate> candidates = new ArrayList<>();
        for (GraphNode entity : entities) {
            nodeById.put(entity.getId(), entity);
            int score = seedScore(entity, seedText.toString(), question);
            if (score > 0) {
                candidates.add(new SeedCandidate(entity.getId(), score));
            }
        }
        candidates.sort(Comparator.comparingInt(SeedCandidate::score).reversed());

        Set<Long> seedIds = new LinkedHashSet<>();
        for (SeedCandidate candidate : candidates) {
            seedIds.add(candidate.nodeId());
            if (seedIds.size() >= MAX_SEEDS) {
                break;
            }
        }
        if (seedIds.isEmpty()) {
            return LocalSearchResult.empty();
        }

        List<GraphEdge> allEdges = graphEdgeRepository.findByProjectIdAndEdgeType(projectId, "ENTITY_REL");
        List<GraphEdge> localEdges = allEdges.stream()
                .filter(edge -> seedIds.contains(edge.getSourceId()) || seedIds.contains(edge.getTargetId()))
                .sorted(Comparator.comparingDouble(GraphEdge::getWeight).reversed())
                .limit(MAX_RELATIONS)
                .toList();

        Set<Long> relatedNodeIds = new LinkedHashSet<>(seedIds);
        for (GraphEdge edge : localEdges) {
            relatedNodeIds.add(edge.getSourceId());
            relatedNodeIds.add(edge.getTargetId());
        }

        List<String> graphFacts = buildGraphFacts(nodeById, seedIds, relatedNodeIds, localEdges);
        List<DocChunk> chunks = collectEvidenceChunks(projectId, seedChunks, nodeById,
                seedIds, relatedNodeIds, localEdges, Math.max(1, extraK));

        log.info("[local] project={} seeds={} relations={} facts={} chunks={}",
                projectId, seedIds.size(), localEdges.size(), graphFacts.size(), chunks.size());
        return new LocalSearchResult(chunks, graphFacts);
    }

    private int seedScore(GraphNode entity, String seedText, String question) {
        String label = entity.getLabel();
        if (label == null || label.isBlank() || label.length() < 2) {
            return 0;
        }
        int score = 0;
        if (question != null && question.contains(label)) {
            score += 100;
        }
        if (seedText.contains(label)) {
            score += 50;
        }
        String summary = entity.getSummary();
        if (summary != null && question != null) {
            for (String token : questionTokens(question)) {
                if (token.length() >= 2 && summary.contains(token)) {
                    score += 3;
                }
            }
        }
        return score;
    }

    private List<String> buildGraphFacts(Map<Long, GraphNode> nodeById,
                                         Set<Long> seedIds,
                                         Set<Long> relatedNodeIds,
                                         List<GraphEdge> localEdges) {
        List<String> facts = new ArrayList<>();
        for (Long id : relatedNodeIds) {
            GraphNode node = nodeById.get(id);
            if (node == null) {
                continue;
            }
            String prefix = seedIds.contains(id) ? "Seed entity" : "Neighbor entity";
            String summary = node.getSummary();
            String type = node.getDocType();
            facts.add(prefix + ": " + node.getLabel()
                    + valuePart("type", type)
                    + valuePart("summary", summary));
        }
        for (GraphEdge edge : localEdges) {
            GraphNode source = nodeById.get(edge.getSourceId());
            GraphNode target = nodeById.get(edge.getTargetId());
            if (source == null || target == null) {
                continue;
            }
            facts.add("Relation: " + source.getLabel()
                    + " --[" + edge.getEdgeType() + "]--> " + target.getLabel()
                    + valuePart("description", edge.getDescription()));
        }
        return facts;
    }

    private List<DocChunk> collectEvidenceChunks(Long projectId,
                                                 List<DocChunk> seedChunks,
                                                 Map<Long, GraphNode> nodeById,
                                                 Set<Long> seedIds,
                                                 Set<Long> relatedNodeIds,
                                                 List<GraphEdge> localEdges,
                                                 int limit) {
        Map<Long, DocChunk> out = new LinkedHashMap<>();
        Set<Long> seenChunkIds = new HashSet<>();
        for (DocChunk chunk : seedChunks) {
            seenChunkIds.add(chunk.getId());
        }

        List<Long> evidenceIds = localEdges.stream()
                .map(GraphEdge::getEvidenceChunkId)
                .filter(id -> id != null && !seenChunkIds.contains(id))
                .distinct()
                .limit(limit)
                .toList();
        for (DocChunk chunk : docChunkRepository.findAllById(evidenceIds)) {
            if (seenChunkIds.add(chunk.getId())) {
                out.put(chunk.getId(), chunk);
            }
        }

        List<Long> labelNodeIds = new ArrayList<>(seedIds);
        for (Long id : relatedNodeIds) {
            if (!seedIds.contains(id)) {
                labelNodeIds.add(id);
            }
        }

        int labelsUsed = 0;
        for (Long nodeId : labelNodeIds) {
            if (out.size() >= limit || labelsUsed >= MAX_NEIGHBOR_LABELS) {
                break;
            }
            GraphNode node = nodeById.get(nodeId);
            if (node == null || node.getLabel() == null || node.getLabel().length() < 2) {
                continue;
            }
            labelsUsed++;
            List<DocChunk> hits = docChunkRepository.searchByKeyword(
                    projectId, node.getLabel(), PageRequest.of(0, PER_LABEL_CHUNKS));
            for (DocChunk hit : hits) {
                if (seenChunkIds.add(hit.getId())) {
                    out.put(hit.getId(), hit);
                    if (out.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private String valuePart(String name, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "; " + name + "=" + value.strip();
    }

    private List<String> questionTokens(String question) {
        Set<String> tokens = new LinkedHashSet<>();
        String cleaned = question.replaceAll("[\\s\\p{Punct}]", "");
        for (int i = 0; i + 1 < cleaned.length(); i++) {
            char c1 = cleaned.charAt(i);
            char c2 = cleaned.charAt(i + 1);
            if (isChinese(c1) && isChinese(c2)) {
                tokens.add("" + c1 + c2);
            }
        }
        return new ArrayList<>(tokens);
    }

    private boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }

    private record SeedCandidate(Long nodeId, int score) {}
}
