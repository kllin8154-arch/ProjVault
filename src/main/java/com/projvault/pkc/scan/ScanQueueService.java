package com.projvault.pkc.scan;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScanQueueService {
    private final ThreadPoolTaskExecutor executor;
    private final ScanTaskRepository repository;

    public ScanQueueService(@Qualifier("scanExecutor") ThreadPoolTaskExecutor executor,
                            ScanTaskRepository repository) {
        this.executor = executor;
        this.repository = repository;
    }

    public ScanQueueSnapshot snapshot() {
        List<ScanTask> pending = repository.findByStatusInOrderByIdAsc(List.of(ScanStatus.PENDING));
        List<ScanQueueSnapshot.QueuedScan> rows = java.util.stream.IntStream.range(0, pending.size())
                .mapToObj(index -> {
                    ScanTask task = pending.get(index);
                    return new ScanQueueSnapshot.QueuedScan(task.getId(), task.getProjectId(), index + 1,
                            task.getMode().name(), task.getCreatedAt().toString());
                }).toList();
        return new ScanQueueSnapshot(executor.getActiveCount(), executor.getQueueSize(),
                executor.getQueueCapacity(), rows);
    }
}
