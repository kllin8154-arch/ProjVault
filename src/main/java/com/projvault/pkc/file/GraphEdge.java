package com.projvault.pkc.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 知识图谱边（pkc_graph_edge）。
 * edgeType: MENTIONS（FILE → SERVICE）
 */
@Entity
@Table(name = "pkc_graph_edge",
        indexes = {
                @Index(columnList = "projectId"),
                @Index(columnList = "sourceId"),
                @Index(columnList = "targetId")
        })
public class GraphEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    private Long scanId;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Long targetId;

    /** MENTIONS / RELATED */
    @Column(nullable = false, length = 32)
    private String edgeType;

    @Column(length = 1024)
    private String description;

    private Long evidenceChunkId;

    private double weight = 1.0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Getters / Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getEdgeType() { return edgeType; }
    public void setEdgeType(String edgeType) { this.edgeType = edgeType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getEvidenceChunkId() { return evidenceChunkId; }
    public void setEvidenceChunkId(Long evidenceChunkId) { this.evidenceChunkId = evidenceChunkId; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
