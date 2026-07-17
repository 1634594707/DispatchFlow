package com.fsd.dispatch.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.infra.DispatchLockService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务超时调度器：定期扫描长期阻塞在 PENDING / MANUAL_PENDING 状态的任务，
 * 标记为 FAILED(TIMEOUT) 并记录异常，避免任务永久阻塞队列。
 *
 * <p>验收标准：不再有任务永久阻塞。</p>
 */
@Component
public class TaskTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskTimeoutScheduler.class);
    private static final String FAIL_REASON_CODE = "TIMEOUT";
    private static final String EXCEPTION_TYPE = "TASK_TIMEOUT";

    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchExceptionService dispatchExceptionService;
    private final DispatchLockService dispatchLockService;

    /** 任务超时阈值（分钟），默认 30 分钟。 */
    @Value("${fsd.automation.task-timeout-minutes:30}")
    private int timeoutMinutes;

    public TaskTimeoutScheduler(DispatchTaskMapper dispatchTaskMapper,
                                DispatchExceptionService dispatchExceptionService,
                                DispatchLockService dispatchLockService) {
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.dispatchExceptionService = dispatchExceptionService;
        this.dispatchLockService = dispatchLockService;
    }

    /**
     * 每 10 分钟扫描一次超时任务。
     */
    @Scheduled(fixedDelayString = "${fsd.automation.task-timeout-check-ms:600000}")
    public void timeoutStaleTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<DispatchTaskEntity> staleTasks = dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTaskEntity>()
                        .in(DispatchTaskEntity::getStatus,
                                DispatchTaskStatus.PENDING.name(),
                                DispatchTaskStatus.MANUAL_PENDING.name())
                        .eq(DispatchTaskEntity::getDeleted, 0)
                        .lt(DispatchTaskEntity::getCreatedAt, cutoff));
        if (staleTasks.isEmpty()) {
            return;
        }
        log.warn("TaskTimeout: found {} stale task(s) older than {} minutes, marking TIMEOUT",
                staleTasks.size(), timeoutMinutes);
        for (DispatchTaskEntity task : staleTasks) {
            timeoutTask(task);
        }
    }

    private void timeoutTask(DispatchTaskEntity task) {
        String lockToken = null;
        try {
            lockToken = dispatchLockService.acquireTaskLock(task.getId());
            DispatchTaskEntity fresh = dispatchTaskMapper.selectById(task.getId());
            if (fresh == null || fresh.getDeleted() == 1) {
                return;
            }
            String currentStatus = fresh.getStatus();
            if (!DispatchTaskStatus.PENDING.name().equals(currentStatus)
                    && !DispatchTaskStatus.MANUAL_PENDING.name().equals(currentStatus)) {
                // 状态已变化，跳过
                return;
            }
            String beforeStatus = currentStatus;
            fresh.setStatus(DispatchTaskStatus.FAILED.name());
            fresh.setFailReasonCode(FAIL_REASON_CODE);
            fresh.setFailReasonMsg("任务超时：在 " + beforeStatus + " 状态超过 " + timeoutMinutes + " 分钟未处理");
            fresh.setFinishTime(LocalDateTime.now());
            dispatchTaskMapper.updateById(fresh);

            String message = "任务 " + fresh.getTaskNo() + " 在 " + beforeStatus
                    + " 状态超过 " + timeoutMinutes + " 分钟未处理，已自动标记超时";
            dispatchExceptionService.recordException(
                    fresh.getId(), fresh.getOrderId(), fresh.getVehicleId(),
                    EXCEPTION_TYPE, message);
            log.warn("TaskTimeout: task {} ({}) marked TIMEOUT (was {})",
                    fresh.getId(), fresh.getTaskNo(), beforeStatus);
        } catch (Exception ex) {
            log.error("TaskTimeout: failed to timeout task {}: {}", task.getId(), ex.getMessage(), ex);
        } finally {
            if (lockToken != null) {
                try {
                    dispatchLockService.releaseTaskLock(task.getId(), lockToken);
                } catch (Exception ignored) {
                    // 释放锁失败不影响主流程
                }
            }
        }
    }
}
