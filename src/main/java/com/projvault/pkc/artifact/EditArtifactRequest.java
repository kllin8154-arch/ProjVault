package com.projvault.pkc.artifact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EditArtifactRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 500000)
    private String content;

    @Size(max = 1000)
    private String comment;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
