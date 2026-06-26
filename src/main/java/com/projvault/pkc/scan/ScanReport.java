package com.projvault.pkc.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 扫描报告（pkc_scan_report）：P7 阶段生成，每次扫描一份，JSON 全文落库。
 */
@Entity
@Table(name = "pkc_scan_report")
public class ScanReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long scanId;

    @Column(nullable = false)
    private Long projectId;

    /** 报告 JSON 全文 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String reportJson;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Getters / Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getReportJson() { return reportJson; }
    public void setReportJson(String reportJson) { this.reportJson = reportJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
