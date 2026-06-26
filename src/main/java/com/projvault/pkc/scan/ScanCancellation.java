package com.projvault.pkc.scan;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫描取消登记表（进程内）。
 *
 * 不能用 ScanTask.status 作为取消信号：流水线各阶段会反复 save(task)（RUNNING/PHASE），
 * 会把外部写入的 CANCELLED 覆盖掉。故改用独立的内存集合作为权威取消标记，
 * 流水线只读它、不覆盖它；任务结束时 clear。
 */
@Component
public class ScanCancellation {

    private final Set<Long> cancelled = ConcurrentHashMap.newKeySet();

    public void request(Long taskId) { if (taskId != null) cancelled.add(taskId); }

    public boolean isCancelled(Long taskId) { return taskId != null && cancelled.contains(taskId); }

    public void clear(Long taskId) { if (taskId != null) cancelled.remove(taskId); }
}
