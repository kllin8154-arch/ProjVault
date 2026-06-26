package com.projvault.pkc.file;

/**
 * 文件变更类型（P2 指纹对比结果，方案 §7 P2）。
 * RENAMED 通过 sha256 命中旧记录识别（内容不变、路径改变）。
 */
public enum ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    UNCHANGED
}
