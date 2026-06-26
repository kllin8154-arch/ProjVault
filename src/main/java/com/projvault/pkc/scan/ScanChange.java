package com.projvault.pkc.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 单次扫描中某个文件的变更记录（pkc_scan_change）。
 * 仅持久化有意义的变更：ADDED / MODIFIED / DELETED / RENAMED，跳过 UNCHANGED。
 */
@Entity
@Table(name = "pkc_scan_change",
        indexes = {
            @Index(columnList = "scanId"),
            @Index(columnList = "projectId, scanId")
        })
public class ScanChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scanId;

    @Column(nullable = false)
    private Long projectId;

    /** 相对项目根目录路径 */
    @Column(nullable = false, length = 1024)
    private String relPath;

    @Column(nullable = false, length = 255)
    private String name;

    /** ADDED / MODIFIED / DELETED / RENAMED */
    @Column(nullable = false, length = 16)
    private String changeType;

    /** 仅 RENAMED 时非 null：变更前的路径 */
    @Column(length = 1024)
    private String oldRelPath;

    private long fileSize;

    @Column(length = 64)
    private String sha256;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getRelPath() { return relPath; }
    public void setRelPath(String relPath) { this.relPath = relPath; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getOldRelPath() { return oldRelPath; }
    public void setOldRelPath(String oldRelPath) { this.oldRelPath = oldRelPath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
