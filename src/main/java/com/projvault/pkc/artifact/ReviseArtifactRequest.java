package com.projvault.pkc.artifact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReviseArtifactRequest {

    @NotBlank
    @Size(max = 4000)
    private String instructions;

    @Size(max = 255)
    private String title;

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
