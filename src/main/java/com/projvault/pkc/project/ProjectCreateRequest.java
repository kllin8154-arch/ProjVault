package com.projvault.pkc.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建项目请求体。
 */
public record ProjectCreateRequest(
        @NotBlank(message = "项目编码不能为空") @Size(max = 64) String code,
        @NotBlank(message = "项目名称不能为空") @Size(max = 128) String name,
        @NotBlank(message = "资料根目录不能为空") @Size(max = 512) String rootPath) {
}
