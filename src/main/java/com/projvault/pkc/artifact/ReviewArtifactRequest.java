package com.projvault.pkc.artifact;

import jakarta.validation.constraints.NotBlank;

public class ReviewArtifactRequest {

    @NotBlank
    private String status;

    private String comment;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
