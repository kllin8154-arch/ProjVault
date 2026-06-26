package com.projvault.pkc.scan;

/**
 * 扫描任务状态机：PENDING → RUNNING → SUCCESS / FAILED / CANCELLED。
 */
public enum ScanStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}
