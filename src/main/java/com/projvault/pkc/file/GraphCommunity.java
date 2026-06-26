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
 * 实体图谱社区（pkc_graph_community）。
 * 由 CommunityService 用标签传播(LPA)在 ENTITY 子图上聚类，每社区一段 LLM 摘要，
 * 供 GraphRAG Global（宏观/主题类）问答的 map-reduce 上下文使用。
 */
@Entity
@Table(name = "pkc_graph_community", indexes = @Index(columnList = "projectId"))
public class GraphCommunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    private Long scanId;

    /** 社区编号（项目内） */
    @Column(nullable = false)
    private int communityNo;

    /** 成员实体数 */
    private int size;

    /** 代表成员实体名（按度数降序，逗号分隔，截断） */
    @Column(length = 1024)
    private String members;

    /** 社区主题摘要（LLM 生成） */
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public int getCommunityNo() { return communityNo; }
    public void setCommunityNo(int communityNo) { this.communityNo = communityNo; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getMembers() { return members; }
    public void setMembers(String members) { this.members = members; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
