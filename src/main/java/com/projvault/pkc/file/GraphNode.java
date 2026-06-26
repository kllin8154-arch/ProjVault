package com.projvault.pkc.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 知识图谱节点（pkc_graph_node）。
 * nodeType: FILE（文件资产）| SERVICE（配置项归纳的服务端点）
 */
@Entity
@Table(name = "pkc_graph_node",
        uniqueConstraints = @UniqueConstraint(columnNames = {"projectId", "nodeKey"}),
        indexes = {
                @Index(columnList = "projectId"),
                @Index(columnList = "projectId, nodeType")
        })
public class GraphNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    private Long scanId;

    /** FILE / SERVICE */
    @Column(nullable = false, length = 32)
    private String nodeType;

    /** 项目内唯一标识：file:{fileId} 或 svc:{keyType}:{keyValue} */
    @Column(nullable = false, length = 512)
    private String nodeKey;

    /** 显示名称 */
    @Column(length = 255)
    private String label;

    /** FILE 节点：docType；SERVICE 节点：keyType（JDBC_URL/REDIS_URL/IP_PORT/HOST_PORT）*/
    @Column(length = 64)
    private String docType;

    @Column(length = 512)
    private String tags;

    @Column(length = 1024)
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Getters / Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
