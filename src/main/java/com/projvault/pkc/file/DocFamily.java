package com.projvault.pkc.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 文档版本族（pkc_doc_family）：同一文档不同版本的物理文件归并为一个族。
 * 聚类算法见 FamilyClusterService：文件名规整化 + 同项目同规整名 → 同族。
 */
@Entity
@Table(name = "pkc_doc_family")
public class DocFamily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    /** 最近一次更新该族的扫描任务 ID */
    private Long lastScanId;

    /** 规整化文件名（去扩展名/版本号/日期/副本后缀后的核心名），用于聚类 key */
    @Column(nullable = false, length = 512)
    private String familyName;

    /** 族内主要文档类型（取成员中频率最高的 docType） */
    @Column(length = 64)
    private String docType;

    /** 当前族内文件数量 */
    private int fileCount;

    /**
     * 推荐有效版文件 ID：
     *  - 优先取 mtime 最新的成员
     *  - 后续支持人工标记覆盖
     */
    private Long effectiveFileId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ─── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getLastScanId() { return lastScanId; }
    public void setLastScanId(Long lastScanId) { this.lastScanId = lastScanId; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }

    public Long getEffectiveFileId() { return effectiveFileId; }
    public void setEffectiveFileId(Long effectiveFileId) { this.effectiveFileId = effectiveFileId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
