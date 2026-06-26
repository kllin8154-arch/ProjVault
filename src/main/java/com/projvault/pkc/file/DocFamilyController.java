package com.projvault.pkc.file;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档版本族 REST API。
 */
@RestController
@RequestMapping("/api/pkc")
public class DocFamilyController {

    private final DocFamilyRepository docFamilyRepository;
    private final FileAssetRepository fileAssetRepository;

    public DocFamilyController(DocFamilyRepository docFamilyRepository,
                               FileAssetRepository fileAssetRepository) {
        this.docFamilyRepository = docFamilyRepository;
        this.fileAssetRepository = fileAssetRepository;
    }

    /**
     * GET /api/pkc/projects/{id}/doc-families
     * 返回项目内所有版本族（按文件数降序）。
     */
    @GetMapping("/projects/{id}/doc-families")
    @RequirePerm("pkc:file:read")
    public ApiResponse<?> listFamilies(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<DocFamily> familyPage = docFamilyRepository
                .findByProjectIdOrderByFileCountDescFamilyNameAsc(
                        id, PageRequest.of(page, size));

        Page<Map<String, Object>> result = familyPage.map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",              f.getId());
            m.put("familyName",      f.getFamilyName());
            m.put("docType",         f.getDocType());
            m.put("fileCount",       f.getFileCount());
            m.put("effectiveFileId", f.getEffectiveFileId());
            m.put("updatedAt",       f.getUpdatedAt());
            return m;
        });

        return ApiResponse.ok(result);
    }

    /**
     * GET /api/pkc/doc-families/{id}
     * 返回族详情 + 成员文件列表（按 mtime 降序）。
     */
    @GetMapping("/doc-families/{id}")
    @RequirePerm("pkc:file:read")
    public ApiResponse<?> getFamily(@PathVariable Long id) {

        DocFamily family = docFamilyRepository.findById(id).orElse(null);
        if (family == null) {
            return ApiResponse.error(404, "版本族不存在");
        }

        List<FileAsset> members = fileAssetRepository.findByFamilyIdOrderByMtimeDesc(id);

        List<Map<String, Object>> memberDtos = members.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           f.getId());
            m.put("name",         f.getName());
            m.put("relPath",      f.getRelPath());
            m.put("versionLabel", f.getVersionLabel());
            m.put("size",         f.getSize());
            m.put("mtime",        f.getMtime());
            m.put("docType",      f.getDocType());
            m.put("parseStatus",  f.getParseStatus());
            m.put("summary",      f.getSummary());
            m.put("isEffective",  f.getId() != null && f.getId().equals(family.getEffectiveFileId()));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id",              family.getId());
        resp.put("familyName",      family.getFamilyName());
        resp.put("docType",         family.getDocType());
        resp.put("fileCount",       family.getFileCount());
        resp.put("effectiveFileId", family.getEffectiveFileId());
        resp.put("updatedAt",       family.getUpdatedAt());
        resp.put("members",         memberDtos);

        return ApiResponse.ok(resp);
    }
}
