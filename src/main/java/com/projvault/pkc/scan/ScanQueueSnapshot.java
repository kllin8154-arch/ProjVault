package com.projvault.pkc.scan;

import java.util.List;

public record ScanQueueSnapshot(
        int activeWorkers,
        int queuedTasks,
        int queueCapacity,
        List<QueuedScan> pendingScans) {
    public record QueuedScan(Long scanId, Long projectId, int position, String mode, String createdAt) {}
}
