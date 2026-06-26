package com.projvault.pkc.artifact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ArtifactMoveRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    private Long folderId;

    @Size(max = 255)
    private String fileName;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
