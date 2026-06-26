package com.projvault.pkc.file;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 配置提取条目（pkc_config_item）。
 * reviewStatus: PENDING（待确认）/ CONFIRMED（已确认为真实配置）/ REJECTED（已驳回为误报）
 */
@Entity
@Table(name = "pkc_config_item",
        indexes = {
                @Index(columnList = "projectId"),
                @Index(columnList = "fileId"),
                @Index(columnList = "keyType"),
                @Index(columnList = "reviewStatus")
        })
public class ConfigItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long fileId;

    private Long scanId;

    /** IP_PORT / URL / JDBC_URL / HOST_PORT / REDIS_URL / MQ_URL */
    @Column(nullable = false, length = 32)
    private String keyType;

    @Column(nullable = false, length = 1024)
    private String keyValue;

    /** 提取位置前后各 80 字符上下文 */
    @Column(length = 512)
    private String context;

    /** PENDING / CONFIRMED / REJECTED */
    @Column(nullable = false, length = 16)
    private String reviewStatus = "PENDING";

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }

    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
