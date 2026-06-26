package com.projvault.pkc.artifact;

public enum ArtifactFormat {
    MARKDOWN("md", "text/markdown;charset=UTF-8"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    HTML("html", "text/html;charset=UTF-8"),
    SQL("sql", "text/plain;charset=UTF-8"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String mediaType;

    ArtifactFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String getExtension() { return extension; }
    public String getMediaType() { return mediaType; }
}
