package com.projvault.pkc.scan;

import com.projvault.common.BusinessException;
import com.projvault.pkc.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 扫描任务服务：创建任务并触发异步流水线。
 * 约束：同一项目同时只允许一个进行中的扫描任务。
 */
@Service
public class ScanTaskService {

    private final ScanTaskRepository scanTaskRepository;
    private final ProjectService projectService;
    private final ScanPipelineRunner pipelineRunner;
    private final ScanCancellation scanCancellation;

    public ScanTaskService(ScanTaskRepository scanTaskRepository,
                           ProjectService projectService,
                           ScanPipelineRunner pipelineRunner,
                           ScanCancellation scanCancellation) {
        this.scanTaskRepository = scanTaskRepository;
        this.projectService = projectService;
        this.pipelineRunner = pipelineRunner;
        this.scanCancellation = scanCancellation;
    }

    @Transactional
    public ScanTask start(Long projectId, ScanMode mode, Boolean entity) {
        projectService.getById(projectId);
        // 仅当存在“未被请求取消”的进行中任务时才阻止：已点停止但仍在收尾的任务不算阻塞，
        // 允许用户停止后立即重新扫描（配合流水线按文件级响应取消，重叠窗口极小）。
        boolean running = scanTaskRepository.findByProjectIdOrderByIdDesc(projectId).stream()
                .filter(s -> s.getStatus() == ScanStatus.PENDING || s.getStatus() == ScanStatus.RUNNING)
                .anyMatch(s -> !scanCancellation.isCancelled(s.getId()));
        if (running) {
            throw new BusinessException("该项目已有进行中的扫描任务");
        }
        ScanTask task = new ScanTask();
        task.setProjectId(projectId);
        task.setMode(mode == null ? ScanMode.INCREMENTAL : mode);
        task.setEntityExtraction(entity != null && entity);
        task = scanTaskRepository.save(task);
        Long taskId = task.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pipelineRunner.run(taskId);
                }
            });
        } else {
            pipelineRunner.run(taskId);
        }
        return task;
    }

    @Transactional
    public ScanTask cancel(Long id) {
        ScanTask task = getById(id);
        if (task.getStatus() == ScanStatus.PENDING || task.getStatus() == ScanStatus.RUNNING) {
            scanCancellation.request(id);          // 权威取消标记（流水线只读不覆盖）
            task.setStatus(ScanStatus.CANCELLED);  // DB 状态供 UI 展示（可能被阶段 save 暂时覆盖，最终由 finally 落 CANCELLED）
            scanTaskRepository.save(task);
        }
        return task;
    }

    public ScanTask getById(Long id) {
        return scanTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "扫描任务不存在: " + id));
    }

    public List<ScanTask> listByProject(Long projectId) {
        return scanTaskRepository.findByProjectIdOrderByIdDesc(projectId);
    }
}
