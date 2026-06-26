package com.projvault.pkc.scan;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件变更记录 REST API。
 *
 * GET /api/pkc/scans/{id}/changes      — 指定扫描的变更列表（按类型+路径排序）
 * GET /api/pkc/projects/{id}/changes   — 项目最近 N 次扫描的变更（倒序）
 */
@RestController
@RequestMapping("/api/pkc")
public class ScanChangeController {

    private final ScanChangeRepository scanChangeRepository;

    public ScanChangeController(ScanChangeRepository scanChangeRepository) {
        this.scanChangeRepository = scanChangeRepository;
    }

    @GetMapping("/scans/{id}/changes")
    @RequirePerm("pkc:file:read")
    public ApiResponse<?> byScan(@PathVariable Long id) {
        List<ScanChange> records =
                scanChangeRepository.findByScanIdOrderByChangeTypeAscRelPathAsc(id);
        return ApiResponse.ok(toDto(records));
    }

    @GetMapping("/projects/{id}/changes")
    @RequirePerm("pkc:file:read")
    public ApiResponse<?> byProject(@PathVariable Long id) {
        List<ScanChange> records =
                scanChangeRepository.findByProjectIdOrderByScanIdDescCreatedAtDesc(id);
        return ApiResponse.ok(toDto(records));
    }

    private List<Map<String, Object>> toDto(List<ScanChange> records) {
        return records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",         r.getId());
            m.put("scanId",     r.getScanId());
            m.put("projectId",  r.getProjectId());
            m.put("changeType", r.getChangeType());
            m.put("relPath",    r.getRelPath());
            m.put("name",       r.getName());
            m.put("oldRelPath", r.getOldRelPath());
            m.put("fileSize",   r.getFileSize());
            m.put("sha256",     r.getSha256());
            m.put("createdAt",  r.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
    }
}
