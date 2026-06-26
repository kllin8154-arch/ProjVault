package com.projvault.pkc.artifact;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class GenerateArtifactRequest {

    @NotBlank
    private String title;

    @NotNull
    private ArtifactType artifactType;

    @NotNull
    private ArtifactFormat format;

    private String instructions;
    private String outputDir;
    private String fileName;

    @Min(5)
    @Max(30)
    private int topK = 18;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public ArtifactType getArtifactType() { return artifactType; }
    public void setArtifactType(ArtifactType artifactType) { this.artifactType = artifactType; }
    public ArtifactFormat getFormat() { return format; }
    public void setFormat(ArtifactFormat format) { this.format = format; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
}
