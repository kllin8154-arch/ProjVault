package com.projvault.ai;

/**
 * 配置项提取结果（服务器 IP / 端口 / 路径 / URL / 账号 / 参数 / 版本）。
 *
 * @param itemType   ip | port | path | url | account | param | version
 * @param keyName    配置项名称，如 "redis.port"、"生产库地址"
 * @param value      原始值（敏感值由上层负责加密与脱敏，本层只传递）
 * @param envHint    环境线索：现场生产 / 财评 / 测试 / 开发，可为 null
 * @param serverHint 主机线索：主机名或用途描述，可为 null
 * @param confidence 置信度 0.0 - 1.0
 */
public record ExtractedItem(
        String itemType,
        String keyName,
        String value,
        String envHint,
        String serverHint,
        double confidence) {
}
