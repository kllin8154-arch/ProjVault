package com.projvault.pkc.scan;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 扫描任务接口。
 */
@RestController
@RequestMapping("/api/pkc")
public class ScanController {

    private final ScanTaskService scanTaskService;
    private final ScanQueueService scanQueueService;

    public ScanController(ScanTaskService scanTaskService, ScanQueueService scanQueueService) {
        this.scanTaskService = scanTaskService;
        this.scanQueueService = scanQueueService;
    }

    @PostMapping("/projects/{projectId}/scans")
    @RequirePerm("pkc:scan:start")
    public ApiResponse<ScanTask> start(@PathVariable Long projectId,
                                       @RequestParam(defaultValue = "INCREMENTAL") ScanMode mode,
                                       @RequestParam(required = false) Boolean entity) {
        return ApiResponse.ok(scanTaskService.start(projectId, mode, entity));
    }

    @GetMapping("/projects/{projectId}/scans")
    @RequirePerm("pkc:project:view")
    public ApiResponse<List<ScanTask>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(scanTaskService.listByProject(projectId));
    }

    @PostMapping("/scans/{id}/cancel")
    @RequirePerm("pkc:scan:start")
    public ApiResponse<ScanTask> cancel(@PathVariable Long id) {
        return ApiResponse.ok(scanTaskService.cancel(id));
    }

    @GetMapping("/scans/{id}")
    @RequirePerm("pkc:project:view")
    public ApiResponse<ScanTask> get(@PathVariable Long id) {
        return ApiResponse.ok(scanTaskService.getById(id));
    }

    @GetMapping("/scan-queue")
    @RequirePerm("pkc:project:view")
    public ApiResponse<ScanQueueSnapshot> queue() {
        return ApiResponse.ok(scanQueueService.snapshot());
    }
}
