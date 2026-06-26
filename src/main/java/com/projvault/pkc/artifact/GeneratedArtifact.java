package com.projvault.pkc.artifact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "pkc_generated_artifact", indexes = @Index(columnList = "projectId, createdAt"))
public class GeneratedArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 48)
    private String artifactType;

    @Column(nullable = false, length = 16)
    private String format;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(nullable = false, length = 1024)
    private String relativePath;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sourceFilesJson = "[]";

    @Column(columnDefinition = "TEXT")
    private String evidenceJson = "[]";

    @Column(columnDefinition = "TEXT")
    private String qualityJson;

    @Column(columnDefinition = "TEXT")
    private String contentText;

    @Column(length = 16)
    private String qualityStatus;

    private Long parentArtifactId;

    private Long rootArtifactId;

    @Column
    private int versionNo = 1;

    @Column(nullable = false, length = 16)
    private String reviewStatus = "DRAFT";

    @Column(length = 1000)
    private String reviewComment;

    @Column(length = 64)
    private String sha256;

    private long fileSize;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    private LocalDateTime previewedAt;

    private LocalDateTime deletedAt;

    @Column(length = 1024)
    private String originalRelativePath;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getArtifactType() { return artifactType; }
    public void setArtifactType(String artifactType) { this.artifactType = artifactType; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getSourceFilesJson() { return sourceFilesJson; }
    public void setSourceFilesJson(String sourceFilesJson) { this.sourceFilesJson = sourceFilesJson; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getQualityJson() { return qualityJson; }
    public void setQualityJson(String qualityJson) { this.qualityJson = qualityJson; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public String getQualityStatus() { return qualityStatus; }
    public void setQualityStatus(String qualityStatus) { this.qualityStatus = qualityStatus; }
    public Long getParentArtifactId() { return parentArtifactId; }
    public void setParentArtifactId(Long parentArtifactId) { this.parentArtifactId = parentArtifactId; }
    public Long getRootArtifactId() { return rootArtifactId; }
    public void setRootArtifactId(Long rootArtifactId) { this.rootArtifactId = rootArtifactId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getPreviewedAt() { return previewedAt; }
    public void setPreviewedAt(LocalDateTime previewedAt) { this.previewedAt = previewedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public String getOriginalRelativePath() { return originalRelativePath; }
    public void setOriginalRelativePath(String originalRelativePath) { this.originalRelativePath = originalRelativePath; }
}
