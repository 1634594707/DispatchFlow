package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.event.DispatchEventType;
import com.fsd.dispatch.infra.DispatchReportIdempotencyService;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import com.fsd.dispatch.service.DispatchTaskStateService;
import com.fsd.dispatch.service.PeakModeService;
import com.fsd.order.service.OrderStateService;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleReportService;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleReportResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleReportServiceImpl implements VehicleReportService {

    private final VehicleService vehicleService;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchTaskStateService dispatchTaskStateService;
    private final DispatchTaskOperateLogService operateLogService;
    private final DispatchExceptionService dispatchExceptionService;
    private final OrderStateService orderStateService;
    private final DispatchReportIdempotencyService reportIdempotencyService;
    private final DispatchEventPublisher eventPublisher;
    private final PeakModeService peakModeService;

    public VehicleReportServiceImpl(VehicleService vehicleService,
                                    DispatchTaskMapper dispatchTaskMapper,
                                    DispatchTaskStateService dispatchTaskStateService,
                                    DispatchTaskOperateLogService operateLogService,
                                    DispatchExceptionService dispatchExceptionService,
                                    OrderStateService orderStateService,
                                    DispatchReportIdempotencyService reportIdempotencyService,
                                    DispatchEventPublisher eventPublisher,
                                    PeakModeService peakModeService) {
        this.vehicleService = vehicleService;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.dispatchTaskStateService = dispatchTaskStateService;
        this.operateLogService = operateLogService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.orderStateService = orderStateService;
        this.reportIdempotencyService = reportIdempotencyService;
        this.eventPublisher = eventPublisher;
        this.peakModeService = peakModeService;
    }

    @Override
    @Transactional
    public VehicleReportResponse handleReport(VehicleReportRequest request) {
        if (!reportIdempotencyService.markIfFirstReport(request)) {
            return VehicleReportResponse.builder()
                    .vehicleCode(request.getVehicleCode())
                    .vehicleDispatchStatus(request.getDispatchStatus())
                    .message("Duplicate report ignored")
                    .build();
        }

        try {
            return processReport(request);
        } catch (Exception ex) {
            reportIdempotencyService.releaseReport(request);
            throw ex;
        }
    }

    private VehicleReportResponse processReport(VehicleReportRequest request) {
        VehicleEntity vehicleEntity = vehicleService.updateSnapshot(request);

        String taskStatus = null;
        String orderStatus = null;
        if (request.getTaskId() != null) {
            DispatchTaskEntity taskEntity = dispatchTaskStateService.getTask(request.getTaskId());
            switch (request.getReportType()) {
                case "START_EXECUTE" -> {
                    dispatchTaskStateService.assertCanStartExecute(taskEntity);
                    String beforeStatus = taskEntity.getStatus();
                    taskEntity.setStatus(DispatchTaskStatus.EXECUTING.name());
                    taskEntity.setStartTime(request.getReportTime());
                    updateIfStatusUnchanged(taskEntity, beforeStatus);
                    orderStateService.markInProgress(taskEntity.getOrderId());
                    operateLogService.record(taskEntity.getId(), "START_EXECUTE", beforeStatus, taskEntity.getStatus(),
                            "VEHICLE", request.getVehicleCode(), request.getVehicleCode(), request.getResultMessage());
                    eventPublisher.publish(DispatchEventType.TASK_EXECUTING, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));
                    taskStatus = taskEntity.getStatus();
                    orderStatus = "IN_PROGRESS";
                }
                case "TASK_SUCCESS" -> {
                    dispatchTaskStateService.assertCanFinish(taskEntity);
                    String beforeStatus = taskEntity.getStatus();
                    taskEntity.setStatus(DispatchTaskStatus.SUCCESS.name());
                    taskEntity.setFinishTime(request.getReportTime());
                    taskEntity.setPeakModeAtFinish(peakModeService.isPeakMode(resolveTaskParkId(taskEntity)) ? "PEAK" : "NORMAL");
                    updateIfStatusUnchanged(taskEntity, beforeStatus);
                    orderStateService.markCompleted(taskEntity.getOrderId());
                    vehicleService.releaseVehicle(vehicleEntity.getId(), VehicleDispatchStatus.IDLE.name());
                    operateLogService.record(taskEntity.getId(), "FINISH_SUCCESS", beforeStatus, taskEntity.getStatus(),
                            "VEHICLE", request.getVehicleCode(), request.getVehicleCode(), request.getResultMessage());
                    eventPublisher.publish(DispatchEventType.TASK_SUCCESS, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));
                    taskStatus = taskEntity.getStatus();
                    orderStatus = "COMPLETED";
                }
                case "TASK_FAILED" -> {
                    dispatchTaskStateService.assertCanFinish(taskEntity);
                    String beforeStatus = taskEntity.getStatus();
                    int currentRetryCount = taskEntity.getRetryCount() == null ? 0 : taskEntity.getRetryCount();
                    if (currentRetryCount < 3) {
                        // 重试次数未达上限，重置任务为 PENDING 等待再次派单
                        taskEntity.setStatus(DispatchTaskStatus.PENDING.name());
                        taskEntity.setRetryCount(currentRetryCount + 1);
                        taskEntity.setFailReasonCode(request.getResultCode());
                        taskEntity.setFailReasonMsg(request.getResultMessage());
                        taskEntity.setVehicleId(null);
                        taskEntity.setAssignTime(null);
                        updateIfStatusUnchanged(taskEntity, beforeStatus);
                        vehicleService.releaseVehicle(vehicleEntity.getId(), VehicleDispatchStatus.IDLE.name());
                        try {
                            orderStateService.revertToWaitingDispatch(taskEntity.getOrderId());
                        } catch (RuntimeException ignored) {
                            // 订单可能已不在可回退状态，忽略以保证车辆释放成功
                        }
                        dispatchExceptionService.recordException(taskEntity.getId(), taskEntity.getOrderId(), vehicleEntity.getId(),
                                "TASK_EXECUTE_FAILED_RETRY", request.getResultMessage());
                        operateLogService.record(taskEntity.getId(), "TASK_RETRY", beforeStatus, taskEntity.getStatus(),
                                "VEHICLE", request.getVehicleCode(), request.getVehicleCode(),
                                "Retry " + (currentRetryCount + 1) + "/3: " + request.getResultMessage());
                        eventPublisher.publish(DispatchEventType.TASK_FAILED, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));
                        taskStatus = taskEntity.getStatus();
                        orderStatus = "WAITING_DISPATCH";
                    } else {
                        // 重试次数已达上限，标记为 FAILED 终态
                        taskEntity.setStatus(DispatchTaskStatus.FAILED.name());
                        taskEntity.setFinishTime(request.getReportTime());
                        taskEntity.setFailReasonCode(request.getResultCode());
                        taskEntity.setFailReasonMsg(request.getResultMessage());
                        updateIfStatusUnchanged(taskEntity, beforeStatus);
                        orderStateService.markFailed(taskEntity.getOrderId(), request.getResultMessage());
                        vehicleService.releaseVehicle(vehicleEntity.getId(), VehicleDispatchStatus.IDLE.name());
                        dispatchExceptionService.recordException(taskEntity.getId(), taskEntity.getOrderId(), vehicleEntity.getId(),
                                "TASK_EXECUTE_FAILED", request.getResultMessage());
                        operateLogService.record(taskEntity.getId(), "FINISH_FAILED", beforeStatus, taskEntity.getStatus(),
                                "VEHICLE", request.getVehicleCode(), request.getVehicleCode(), request.getResultMessage());
                        eventPublisher.publish(DispatchEventType.TASK_FAILED, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));
                        taskStatus = taskEntity.getStatus();
                        orderStatus = "FAILED";
                    }
                }
                case "OFFLINE" -> {
                    if (taskEntity.getVehicleId() != null) {
                        dispatchExceptionService.recordException(taskEntity.getId(), taskEntity.getOrderId(), vehicleEntity.getId(),
                                "VEHICLE_OFFLINE", "Vehicle went offline during task");
                    }
                    taskStatus = taskEntity.getStatus();
                }
                default -> taskStatus = taskEntity.getStatus();
            }
        }

        return VehicleReportResponse.builder()
                .vehicleCode(vehicleEntity.getVehicleCode())
                .taskStatus(taskStatus)
                .orderStatus(orderStatus)
                .vehicleDispatchStatus(request.getDispatchStatus())
                .build();
    }

    /**
     * 状态迁移只在"库里仍是本次读到的前置状态"时才生效。
     *
     * <p>派单/取消/超时那 6 处入口都先持 {@code acquireTaskLock}，<b>上报路径没有</b>这把锁，此前是无条件
     * {@code updateById}：超时置 FAILED 与车端回报置 SUCCESS 同时到达时后写覆盖前写，还会拿调用方手上的
     * 旧快照把别人刚改的列整行盖回去。把前置状态写进 WHERE ⇒ 真竞态时影响 0 行并抛冲突，
     * 由 {@link #handleReport} 释放幂等标记后交给车端重试，而不是静默写坏。</p>
     */
    private void updateIfStatusUnchanged(DispatchTaskEntity taskEntity, String expectedStatus) {
        int rows = dispatchTaskMapper.update(taskEntity, new LambdaUpdateWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getId, taskEntity.getId())
                .eq(DispatchTaskEntity::getStatus, expectedStatus));
        if (rows == 0) {
            throw new BusinessException("DISPATCH_TASK_STATE_CONFLICT",
                    "任务状态已被并发变更，本次上报未生效: taskId=" + taskEntity.getId());
        }
    }

    private Map<String, Object> buildTaskPayload(DispatchTaskEntity taskEntity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskEntity.getId());
        payload.put("orderId", taskEntity.getOrderId());
        payload.put("parkId", resolveTaskParkId(taskEntity));
        payload.put("vehicleId", taskEntity.getVehicleId());
        payload.put("status", taskEntity.getStatus());
        payload.put("startTime", taskEntity.getStartTime());
        payload.put("finishTime", taskEntity.getFinishTime());
        payload.put("failReasonCode", taskEntity.getFailReasonCode());
        payload.put("failReasonMsg", taskEntity.getFailReasonMsg());
        return payload;
    }

    private Long resolveTaskParkId(DispatchTaskEntity taskEntity) {
        if (taskEntity.getOrderId() == null) {
            return null;
        }
        try {
            return orderStateService.getOrder(taskEntity.getOrderId()).getParkId();
        } catch (Exception ex) {
            return null;
        }
    }
}
