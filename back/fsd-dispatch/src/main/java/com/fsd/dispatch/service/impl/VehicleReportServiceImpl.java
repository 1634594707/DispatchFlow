package com.fsd.dispatch.service.impl;

import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.event.DispatchEventType;
import com.fsd.dispatch.infra.DispatchReportIdempotencyService;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import com.fsd.dispatch.service.DispatchTaskStateService;
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

    public VehicleReportServiceImpl(VehicleService vehicleService,
                                    DispatchTaskMapper dispatchTaskMapper,
                                    DispatchTaskStateService dispatchTaskStateService,
                                    DispatchTaskOperateLogService operateLogService,
                                    DispatchExceptionService dispatchExceptionService,
                                    OrderStateService orderStateService,
                                    DispatchReportIdempotencyService reportIdempotencyService,
                                    DispatchEventPublisher eventPublisher) {
        this.vehicleService = vehicleService;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.dispatchTaskStateService = dispatchTaskStateService;
        this.operateLogService = operateLogService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.orderStateService = orderStateService;
        this.reportIdempotencyService = reportIdempotencyService;
        this.eventPublisher = eventPublisher;
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
                    dispatchTaskMapper.updateById(taskEntity);
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
                    dispatchTaskMapper.updateById(taskEntity);
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
                    taskEntity.setStatus(DispatchTaskStatus.FAILED.name());
                    taskEntity.setFinishTime(request.getReportTime());
                    taskEntity.setFailReasonCode(request.getResultCode());
                    taskEntity.setFailReasonMsg(request.getResultMessage());
                    dispatchTaskMapper.updateById(taskEntity);
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

    private Map<String, Object> buildTaskPayload(DispatchTaskEntity taskEntity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskEntity.getId());
        payload.put("orderId", taskEntity.getOrderId());
        payload.put("vehicleId", taskEntity.getVehicleId());
        payload.put("status", taskEntity.getStatus());
        payload.put("startTime", taskEntity.getStartTime());
        payload.put("finishTime", taskEntity.getFinishTime());
        payload.put("failReasonCode", taskEntity.getFailReasonCode());
        payload.put("failReasonMsg", taskEntity.getFailReasonMsg());
        return payload;
    }
}
