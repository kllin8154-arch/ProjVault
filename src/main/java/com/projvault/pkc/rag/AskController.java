package com.projvault.pkc.rag;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 问答接口。
 * POST /api/pkc/projects/{projectId}/ask          提问
 * GET  /api/pkc/projects/{projectId}/ask/history  查询历史记录
 */
@RestController
@RequestMapping("/api/pkc/projects/{projectId}/ask")
public class AskController {

    private final RagService ragService;
    private final AskHistoryRepository askHistoryRepository;

    public AskController(RagService ragService, AskHistoryRepository askHistoryRepository) {
        this.ragService = ragService;
        this.askHistoryRepository = askHistoryRepository;
    }

    @PostMapping
    @RequirePerm("pkc:project:view")
    public ApiResponse<RagAnswerWithTrace> ask(@PathVariable Long projectId,
                                               @Valid @RequestBody AskRequest request) {
        int topK = request.getTopK() > 0 ? request.getTopK() : 10;
        RagAnswerWithTrace answer = ragService.askWithTrace(projectId, request.getQuestion(), topK, request.getMode());
        return ApiResponse.ok(answer);
    }

    /**
     * 分页查询问答历史，按时间倒序。
     *
     * @param page 页码（0-based，默认 0）
     * @param size 每页条数（默认 20，最大 100）
     */
    @GetMapping("/history")
    @RequirePerm("pkc:project:view")
    public ApiResponse<Page<AskHistoryDTO>> history(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        Page<AskHistory> raw = askHistoryRepository.findByProjectIdOrderByCreatedAtDesc(
                projectId, PageRequest.of(page, size));
        Page<AskHistoryDTO> result = raw.map(AskHistoryDTO::from);
        return ApiResponse.ok(result);
    }
}
