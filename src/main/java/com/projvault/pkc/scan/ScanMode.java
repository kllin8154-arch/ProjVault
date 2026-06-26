package com.projvault.pkc.scan;

/**
 * 扫描模式。
 */
public enum ScanMode {
    /** 全量扫描 */
    FULL,
    /** 增量扫描（基于指纹对比） */
    INCREMENTAL
}
