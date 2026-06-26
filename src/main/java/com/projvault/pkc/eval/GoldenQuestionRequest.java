package com.projvault.pkc.eval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class GoldenQuestionRequest {
    @NotBlank @Size(max = 1000) private String question;
    @NotBlank private String expectedKeywords;
    private String expectedSourcePatterns;
    @Pattern(regexp = "standard|local|global") private String mode = "standard";
    private boolean active = true;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getExpectedKeywords() { return expectedKeywords; }
    public void setExpectedKeywords(String expectedKeywords) { this.expectedKeywords = expectedKeywords; }
    public String getExpectedSourcePatterns() { return expectedSourcePatterns; }
    public void setExpectedSourcePatterns(String expectedSourcePatterns) { this.expectedSourcePatterns = expectedSourcePatterns; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
