package com.projvault.pkc.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用重启后，异步扫描线程不会随数据库状态恢复。
 * 启动时将遗留的 PENDING/RUNNING 任务标记为失败，避免永久阻塞新扫描。
 */
@Component
public class ScanTaskRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScanTaskRecoveryRunner.class);

    private final ScanTaskRepository scanTaskRepository;

    public ScanTaskRecoveryRunner(ScanTaskRepository scanTaskRepository) {
        this.scanTaskRepository = scanTaskRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ScanTask> staleTasks = scanTaskRepository.findByStatusIn(
                List.of(ScanStatus.PENDING, ScanStatus.RUNNING));
        if (staleTasks.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (ScanTask task : staleTasks) {
            task.setStatus(ScanStatus.FAILED);
            task.setFinishedAt(now);
            task.setErrorMsg("应用启动时检测到遗留未完成扫描，已自动恢复为失败状态，可重新触发扫描。");
        }
        scanTaskRepository.saveAll(staleTasks);
        log.warn("已恢复 {} 个遗留扫描任务为 FAILED", staleTasks.size());
    }
}
