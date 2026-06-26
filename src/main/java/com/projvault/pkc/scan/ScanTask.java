package com.projvault.pkc.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 扫描任务（pkc_scan_task）。
 */
@Entity
@Table(name = "pkc_scan_task")
public class ScanTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScanMode mode = ScanMode.INCREMENTAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScanStatus status = ScanStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ScanPhase phase;

    private int totalFiles;

    private int changedFiles;

    private Long totalBytes;

    private Integer largeFileCount;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(length = 1024)
    private String errorMsg;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 本次扫描是否执行实体抽取（GraphRAG），按次扫描控制。可空列：旧数据迁移无 NOT NULL 冲突，null 视为 false */
    private Boolean entityExtraction;

    public boolean isEntityExtraction() { return Boolean.TRUE.equals(entityExtraction); }
    public void setEntityExtraction(boolean entityExtraction) { this.entityExtraction = entityExtraction; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public ScanMode getMode() {
        return mode;
    }

    public void setMode(ScanMode mode) {
        this.mode = mode;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public ScanPhase getPhase() {
        return phase;
    }

    public void setPhase(ScanPhase phase) {
        this.phase = phase;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(int changedFiles) {
        this.changedFiles = changedFiles;
    }

    public long getTotalBytes() { return totalBytes == null ? 0L : totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public int getLargeFileCount() { return largeFileCount == null ? 0 : largeFileCount; }
    public void setLargeFileCount(int largeFileCount) { this.largeFileCount = largeFileCount; }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
