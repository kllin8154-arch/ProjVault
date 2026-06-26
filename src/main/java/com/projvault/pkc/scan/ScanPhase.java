package com.projvault.pkc.scan;

/**
 * 扫描流水线 7 阶段（方案 §7）。
 */
public enum ScanPhase {
    /** P1 枚举与过滤 */
    ENUMERATE,
    /** P2 指纹与增量 */
    FINGERPRINT,
    /** P3 文档解析 */
    PARSE,
    /** P4 语义分析（摘要/标签/图谱片段） */
    SEMANTIC,
    /** P5 结构化提取（配置项/服务器） */
    EXTRACT,
    /** P6 图谱合并与校验 */
    MERGE,
    /** P7 报告与通知 */
    REPORT
}
