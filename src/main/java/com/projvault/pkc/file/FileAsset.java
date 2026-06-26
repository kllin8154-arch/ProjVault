package com.projvault.pkc.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 文件资产（pkc_file）：项目资料根目录下的一个物理文件。
 * 指纹 = sha256；size+mtime 未变则跳过重新哈希（增量优化）。
 */
@Entity
@Table(name = "pkc_file",
        uniqueConstraints = @UniqueConstraint(columnNames = {"projectId", "relPath"}),
        indexes = @Index(columnList = "projectId, sha256"))
public class FileAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    /** 相对项目根目录路径，/ 分隔 */
    @Column(nullable = false, length = 1024)
    private String relPath;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 32)
    private String ext;

    /** doc / config / archive / image / other */
    @Column(length = 16)
    private String category;

    private long size;
    private long mtime;

    @Column(length = 64)
    private String sha256;

    /** 大文件增量校验用的分段内容签名，避免 size+mtime 未变时漏检常见替换。 */
    @Column(length = 64)
    private String contentSignature;

    /** PENDING / PARSED / FAILED / SKIPPED */
    @Column(nullable = false, length = 16)
    private String parseStatus = "PENDING";

    /** P4：LLM 生成的一段式摘要 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** P4：逗号分隔标签，如 "部署,中间件,配置" */
    @Column(length = 512)
    private String tags;

    /** P4：文档类型，如 技术方案/需求文档/配置文件/部署手册/其他 */
    @Column(length = 64)
    private String docType;

    /** IN_SCOPE / REFERENCE / OUT_OF_SCOPE */
    @Column(length = 32)
    private String relevanceStatus;

    private double relevanceScore;

    @Column(length = 512)
    private String relevanceReason;

    /** PROJECT_CORE / PROJECT_ASSUMED / REFERENCE_MATERIAL / OTHER_PROJECT */
    @Column(length = 64)
    private String scopeType;

    @Column(length = 512)
    private String scopeReason;

    private Long firstSeenScan;
    private Long lastSeenScan;

    /** P6：所属文档版本族 ID（null = 单独文件，未归族） */
    private Long familyId;

    /** P6：从文件名中提取的版本标记，如 "V1.0" / "终版" / "0603" */
    @Column(length = 128)
    private String versionLabel;

    @Column(nullable = false)
    private boolean deletedFlag = false;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Getters / Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getRelPath() { return relPath; }
    public void setRelPath(String relPath) { this.relPath = relPath; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExt() { return ext; }
    public void setExt(String ext) { this.ext = ext; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public long getMtime() { return mtime; }
    public void setMtime(long mtime) { this.mtime = mtime; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public String getContentSignature() { return contentSignature; }
    public void setContentSignature(String contentSignature) { this.contentSignature = contentSignature; }

    public String getParseStatus() { return parseStatus; }
    public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getRelevanceStatus() { return relevanceStatus; }
    public void setRelevanceStatus(String relevanceStatus) { this.relevanceStatus = relevanceStatus; }

    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }

    public String getRelevanceReason() { return relevanceReason; }
    public void setRelevanceReason(String relevanceReason) { this.relevanceReason = relevanceReason; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getScopeReason() { return scopeReason; }
    public void setScopeReason(String scopeReason) { this.scopeReason = scopeReason; }

    public Long getFirstSeenScan() { return firstSeenScan; }
    public void setFirstSeenScan(Long firstSeenScan) { this.firstSeenScan = firstSeenScan; }

    public Long getLastSeenScan() { return lastSeenScan; }
    public void setLastSeenScan(Long lastSeenScan) { this.lastSeenScan = lastSeenScan; }

    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }

    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }

    public boolean isDeletedFlag() { return deletedFlag; }
    public void setDeletedFlag(boolean deletedFlag) { this.deletedFlag = deletedFlag; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
