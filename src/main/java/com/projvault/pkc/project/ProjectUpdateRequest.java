package com.projvault.pkc.project;

import jakarta.validation.constraints.Size;

/**
 * 更新项目请求体。字段均可选，仅更新非空字段；code 为唯一键不可修改。
 */
public record ProjectUpdateRequest(
        @Size(max = 128) String name,
        @Size(max = 512) String rootPath,
        @Size(max = 16) String status) {
}
