package com.projvault.pkc.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 扫描报告查询接口。
 * GET /api/pkc/scans/{id}/report                    → 按 scanId 查报告
 * GET /api/pkc/projects/{projectId}/report/latest   → 按项目取最新报告
 */
@RestController
public class ScanReportController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ScanReportRepository scanReportRepository;

    public ScanReportController(ScanReportRepository scanReportRepository) {
        this.scanReportRepository = scanReportRepository;
    }

    @GetMapping("/api/pkc/scans/{id}/report")
    @RequirePerm("pkc:project:view")
    public ResponseEntity<ApiResponse<Object>> getReport(@PathVariable Long id) {
        ScanReport report = scanReportRepository.findByScanId(id).orElse(null);
        if (report == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "报告不存在，请确认扫描已完成"));
        }
        try {
            Object data = MAPPER.readValue(report.getReportJson(), Object.class);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "报告 JSON 解析失败"));
        }
    }

    @GetMapping("/api/pkc/projects/{projectId}/report/latest")
    @RequirePerm("pkc:project:view")
    public ResponseEntity<ApiResponse<Object>> getLatestReport(@PathVariable Long projectId) {
        ScanReport report = scanReportRepository
                .findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElse(null);
        if (report == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "该项目暂无扫描报告，请先触发扫描"));
        }
        try {
            Object data = MAPPER.readValue(report.getReportJson(), Object.class);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "报告 JSON 解析失败"));
        }
    }
}
