package com.projvault.pkc.artifact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ArtifactFolderRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 1024)
    private String relativePath;

    @Size(max = 500)
    private String description;

    private boolean defaultFolder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isDefaultFolder() { return defaultFolder; }
    public void setDefaultFolder(boolean defaultFolder) { this.defaultFolder = defaultFolder; }
}
