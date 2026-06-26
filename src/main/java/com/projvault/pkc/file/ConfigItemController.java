package com.projvault.pkc.file;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import com.projvault.security.ProjectAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置台账 REST API。
 *
 * GET  /api/pkc/projects/{id}/config-items        分页列表（可按 keyType / reviewStatus 过滤）
 * GET  /api/pkc/projects/{id}/config-item-stats   各 keyType 数量统计
 * PATCH /api/pkc/config-items/{id}/confirm        确认为真实配置
 * PATCH /api/pkc/config-items/{id}/reject         驳回为误报
 */
@RestController
@RequestMapping("/api/pkc")
public class ConfigItemController {

    private final ConfigItemRepository configItemRepository;
    private final ProjectAccessService projectAccessService;

    public ConfigItemController(ConfigItemRepository configItemRepository,
                                ProjectAccessService projectAccessService) {
        this.configItemRepository = configItemRepository;
        this.projectAccessService = projectAccessService;
    }

    // ── 分页列表 ──────────────────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/config-items")
    @RequirePerm("pkc:config:view")
    public ApiResponse<Page<ConfigItem>> listConfigItems(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyType,
            @RequestParam(required = false) String reviewStatus) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.ASC, "keyType", "keyValue"));

        Page<ConfigItem> result;
        if (keyType != null && reviewStatus != null) {
            result = configItemRepository.findByProjectIdAndKeyTypeAndReviewStatus(
                    projectId, keyType, reviewStatus, pageable);
        } else if (keyType != null) {
            result = configItemRepository.findByProjectIdAndKeyType(projectId, keyType, pageable);
        } else if (reviewStatus != null) {
            result = configItemRepository.findByProjectIdAndReviewStatus(
                    projectId, reviewStatus, pageable);
        } else {
            result = configItemRepository.findByProjectId(projectId, pageable);
        }
        return ApiResponse.ok(result);
    }

    // ── keyType 统计 ──────────────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/config-item-stats")
    @RequirePerm("pkc:config:view")
    public ApiResponse<Map<String, Long>> configItemStats(@PathVariable Long projectId) {
        List<Object[]> rows = configItemRepository.countByKeyType(projectId);
        Map<String, Long> stats = rows.stream().collect(
                Collectors.toMap(
                        r -> r[0] == null ? "UNKNOWN" : (String) r[0],
                        r -> (Long) r[1]
                ));
        return ApiResponse.ok(stats);
    }

    // ── 批量确认/驳回 ─────────────────────────────────────────────────────────

    @PatchMapping("/config-items/batch")
    @RequirePerm("pkc:config:review")
    public ApiResponse<Integer> batchReview(@RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        String action = String.valueOf(body.get("action"));
        Object idsObj = body.get("ids");
        String status = "confirm".equals(action) ? "CONFIRMED"
                : "reject".equals(action) ? "REJECTED" : null;
        if (status == null || !(idsObj instanceof List<?> idList)) {
            return ApiResponse.error(400, "参数错误");
        }
        int n = 0;
        for (Object o : idList) {
            Long id;
            try {
                id = Long.valueOf(String.valueOf(o));
            } catch (Exception e) {
                continue;
            }
            ConfigItem ci = configItemRepository.findById(id).orElse(null);
            if (ci != null) {
                projectAccessService.requireProject(request, ci.getProjectId());
                ci.setReviewStatus(status);
                ci.setReviewedAt(LocalDateTime.now());
                configItemRepository.save(ci);
                n++;
            }
        }
        return ApiResponse.ok(n);
    }

    // ── 人工确认 ──────────────────────────────────────────────────────────────

    @PatchMapping("/config-items/{id}/confirm")
    @RequirePerm("pkc:config:review")
    public ApiResponse<ConfigItem> confirm(@PathVariable Long id) {
        return configItemRepository.findById(id).map(ci -> {
            ci.setReviewStatus("CONFIRMED");
            ci.setReviewedAt(LocalDateTime.now());
            return ApiResponse.ok(configItemRepository.save(ci));
        }).orElse(ApiResponse.error(404, "配置项不存在: " + id));
    }

    // ── 驳回误报 ──────────────────────────────────────────────────────────────

    @PatchMapping("/config-items/{id}/reject")
    @RequirePerm("pkc:config:review")
    public ApiResponse<ConfigItem> reject(@PathVariable Long id) {
        return configItemRepository.findById(id).map(ci -> {
            ci.setReviewStatus("REJECTED");
            ci.setReviewedAt(LocalDateTime.now());
            return ApiResponse.ok(configItemRepository.save(ci));
        }).orElse(ApiResponse.error(404, "配置项不存在: " + id));
    }
}
