package com.projvault.pkc.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projvault.ai.RagCitation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问答历史响应 DTO：将 AskHistory 的 citationsJson 反序列化为结构化列表。
 */
public class AskHistoryDTO {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long id;
    private Long projectId;
    private String question;
    private String answer;
    private boolean grounded;
    private int topK;
    private int citationCount;
    private List<RagCitation> citations;
    private String createdAt;

    /** 从实体转换为 DTO，失败时 citations 降级为空列表。 */
    public static AskHistoryDTO from(AskHistory h) {
        AskHistoryDTO dto = new AskHistoryDTO();
        dto.id            = h.getId();
        dto.projectId     = h.getProjectId();
        dto.question      = h.getQuestion();
        dto.answer        = h.getAnswer();
        dto.grounded      = h.isGrounded();
        dto.topK          = h.getTopK();
        dto.citationCount = 0;
        dto.createdAt     = h.getCreatedAt() != null ? h.getCreatedAt().toString() : null;
        if (dto.answer == null || dto.answer.isBlank()) {
            dto.answer = RagService.MODEL_UNAVAILABLE_MESSAGE;
        }

        if (h.getCitationsJson() != null && !h.getCitationsJson().isBlank()) {
            try {
                dto.citations = MAPPER.readValue(
                        h.getCitationsJson(),
                        MAPPER.getTypeFactory().constructCollectionType(List.class, RagCitation.class));
            } catch (Exception ignored) {
                dto.citations = List.of();
            }
        } else {
            dto.citations = List.of();
        }
        if (!dto.grounded) {
            dto.citations = List.of();
        } else if (!dto.citations.isEmpty()) {
            double maxScore = dto.citations.stream()
                    .mapToDouble(RagCitation::score)
                    .max()
                    .orElse(0.0);
            Map<String, RagCitation> byFile = new LinkedHashMap<>();
            for (RagCitation citation : dto.citations) {
                boolean primaryEvidence = Double.compare(citation.score(), maxScore) == 0;
                if (!primaryEvidence && !answerNamesCitation(dto.answer, citation.fileName())) {
                    continue;
                }
                RagCitation existing = byFile.get(citation.fileName());
                if (existing == null || citation.score() > existing.score()) {
                    byFile.put(citation.fileName(), citation);
                }
            }
            dto.citations = new ArrayList<>(byFile.values());
        }
        dto.citationCount = dto.citations.size();
        return dto;
    }

    private static boolean answerNamesCitation(String answer, String fileName) {
        if (answer == null || answer.isBlank() || fileName == null || fileName.isBlank()) {
            return false;
        }
        if (answer.contains(fileName)) {
            return true;
        }
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String baseName = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        return !baseName.isBlank() && answer.contains(baseName);
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public boolean isGrounded() { return grounded; }
    public int getTopK() { return topK; }
    public int getCitationCount() { return citationCount; }
    public List<RagCitation> getCitations() { return citations; }
    public String getCreatedAt() { return createdAt; }
}
