package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.event.DispatchEventType;
import com.fsd.dispatch.infra.DispatchLockService;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.service.DispatchTaskStateService;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import com.fsd.dispatch.vo.DispatchTaskCreateResponse;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.service.OrderStateService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchTaskServiceImpl implements DispatchTaskService {

    private static final DateTimeFormatter TASK_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchTaskStateService dispatchTaskStateService;
    private final DispatchTaskOperateLogService operateLogService;
    private final DispatchExceptionService dispatchExceptionService;
    private final OrderStateService orderStateService;
    private final VehicleService vehicleService;
    private final ParkPilotService parkPilotService;
    private final DispatchLockService dispatchLockService;
    private final DispatchEventPublisher eventPublisher;

    public DispatchTaskServiceImpl(DispatchTaskMapper dispatchTaskMapper,
                                   DispatchTaskStateService dispatchTaskStateService,
                                   DispatchTaskOperateLogService operateLogService,
                                   DispatchExceptionService dispatchExceptionService,
                                   OrderStateService orderStateService,
                                   VehicleService vehicleService,
                                   ParkPilotService parkPilotService,
                                   DispatchLockService dispatchLockService,
                                   DispatchEventPublisher eventPublisher) {
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.dispatchTaskStateService = dispatchTaskStateService;
        this.operateLogService = operateLogService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.orderStateService = orderStateService;
        this.vehicleService = vehicleService;
        this.parkPilotService = parkPilotService;
        this.dispatchLockService = dispatchLockService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public DispatchTaskCreateResponse createTask(DispatchTaskCreateRequest request) {
        OrderEntity orderEntity = orderStateService.getOrder(request.getOrderId());
        dispatchTaskStateService.assertCanCreateTask(orderEntity.getId());

        DispatchTaskEntity entity = new DispatchTaskEntity();
        entity.setTaskNo(generateTaskNo());
        entity.setOrderId(request.getOrderId());
        entity.setDispatchType(request.getDispatchType());
        entity.setStatus(DispatchTaskStatus.PENDING.name());
        entity.setManualFlag(0);
        entity.setRetryCount(0);
        entity.setRemark(request.getRemark());
        entity.setVersion(0);
        entity.setDeleted(0);
        dispatchTaskMapper.insert(entity);

        operateLogService.record(entity.getId(), "CREATE_TASK", null, DispatchTaskStatus.PENDING.name(),
                "SYSTEM", "system", "system", request.getRemark());
        eventPublisher.publish(DispatchEventType.TASK_CREATED, String.valueOf(entity.getId()), buildTaskPayload(entity));

        return DispatchTaskCreateResponse.builder()
                .taskId(entity.getId())
                .taskNo(entity.getTaskNo())
                .status(entity.getStatus())
                .build();
    }

    @Override
    @Transactional
    public DispatchTaskAssignResponse autoAssignTask(Long taskId) {
        String lockToken = dispatchLockService.acquireTaskLock(taskId);
        try {
            DispatchTaskEntity taskEntity = dispatchTaskStateService.getTask(taskId);
            dispatchTaskStateService.assertCanAutoAssign(taskEntity);

            String beforeStatus = taskEntity.getStatus();
            taskEntity.setStatus(DispatchTaskStatus.ASSIGNING.name());
            taskEntity.setRetryCount(taskEntity.getRetryCount() == null ? 0 : taskEntity.getRetryCount());
            dispatchTaskMapper.updateById(taskEntity);
            operateLogService.record(taskEntity.getId(), "AUTO_ASSIGN", beforeStatus, taskEntity.getStatus(),
                    "SYSTEM", "system", "system", "Auto assign started");

            List<VehicleEntity> candidates = vehicleService.listAssignableVehicles();
            if (candidates.isEmpty()) {
                moveToManualPending(taskEntity, "AUTO_ASSIGN_NO_VEHICLE", "No assignable vehicle found");
                return DispatchTaskAssignResponse.builder()
                        .taskId(taskEntity.getId())
                        .status(taskEntity.getStatus())
                        .message(taskEntity.getFailReasonMsg())
                        .build();
            }

            OrderEntity orderEntity = orderStateService.getOrder(taskEntity.getOrderId());
            VehicleEntity vehicleEntity;
            try {
                vehicleEntity = parkPilotService.selectNearestVehicle(candidates, orderEntity.getPickupPointId());
            } catch (BusinessException ex) {
                moveToManualPending(taskEntity, "AUTO_ASSIGN_POINT_INVALID", ex.getMessage());
                return DispatchTaskAssignResponse.builder()
                        .taskId(taskEntity.getId())
                        .status(taskEntity.getStatus())
                        .message(taskEntity.getFailReasonMsg())
                        .build();
            }
            try {
                vehicleService.occupyVehicle(vehicleEntity.getId(), taskEntity.getId(), taskEntity.getOrderId());
            } catch (BusinessException ex) {
                moveToManualPending(taskEntity, "AUTO_ASSIGN_CONFLICT", ex.getMessage());
                return DispatchTaskAssignResponse.builder()
                        .taskId(taskEntity.getId())
                        .status(taskEntity.getStatus())
                        .message(taskEntity.getFailReasonMsg())
                        .build();
            }

            taskEntity.setVehicleId(vehicleEntity.getId());
            taskEntity.setStatus(DispatchTaskStatus.ASSIGNED.name());
            taskEntity.setAssignTime(LocalDateTime.now());
            taskEntity.setFailReasonCode(null);
            taskEntity.setFailReasonMsg(null);
            dispatchTaskMapper.updateById(taskEntity);

            orderStateService.markDispatched(taskEntity.getOrderId(), taskEntity.getId());
            operateLogService.record(taskEntity.getId(), "AUTO_ASSIGN", DispatchTaskStatus.ASSIGNING.name(),
                    DispatchTaskStatus.ASSIGNED.name(), "SYSTEM", "system", "system", "Auto assign success");
            eventPublisher.publish(DispatchEventType.TASK_ASSIGNED, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));

            return DispatchTaskAssignResponse.builder()
                    .taskId(taskEntity.getId())
                    .status(taskEntity.getStatus())
                    .vehicleId(vehicleEntity.getId())
                    .message("Auto assign success")
                    .build();
        } finally {
            dispatchLockService.releaseTaskLock(taskId, lockToken);
        }
    }

    @Override
    @Transactional
    public DispatchTaskAssignResponse manualAssignTask(Long taskId, DispatchTaskManualAssignRequest request) {
        String lockToken = dispatchLockService.acquireTaskLock(taskId);
        try {
            DispatchTaskEntity taskEntity = dispatchTaskStateService.getTask(taskId);
            dispatchTaskStateService.assertCanManualAssign(taskEntity);

            vehicleService.occupyVehicle(request.getVehicleId(), taskEntity.getId(), taskEntity.getOrderId());

            String beforeStatus = taskEntity.getStatus();
            taskEntity.setVehicleId(request.getVehicleId());
            taskEntity.setStatus(DispatchTaskStatus.ASSIGNED.name());
            taskEntity.setAssignTime(LocalDateTime.now());
            taskEntity.setManualFlag(1);
            taskEntity.setRetryCount(taskEntity.getRetryCount() == null ? 1 : taskEntity.getRetryCount() + 1);
            taskEntity.setFailReasonCode(null);
            taskEntity.setFailReasonMsg(null);
            dispatchTaskMapper.updateById(taskEntity);

            orderStateService.markDispatched(taskEntity.getOrderId(), taskEntity.getId());
            operateLogService.record(taskEntity.getId(), "MANUAL_ASSIGN", beforeStatus, DispatchTaskStatus.ASSIGNED.name(),
                    "DISPATCHER", request.getOperatorId(), request.getOperatorName(), request.getRemark());
            eventPublisher.publish(DispatchEventType.TASK_MANUAL_ASSIGNED, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));

            return DispatchTaskAssignResponse.builder()
                    .taskId(taskEntity.getId())
                    .status(taskEntity.getStatus())
                    .vehicleId(taskEntity.getVehicleId())
                    .assignTime(taskEntity.getAssignTime())
                    .message("Manual assign success")
                    .build();
        } finally {
            dispatchLockService.releaseTaskLock(taskId, lockToken);
        }
    }

    @Override
    public DispatchTaskDetailResponse getTaskDetail(Long taskId) {
        DispatchTaskEntity taskEntity = dispatchTaskStateService.getTask(taskId);
        return DispatchTaskDetailResponse.builder()
                .taskId(taskEntity.getId())
                .taskNo(taskEntity.getTaskNo())
                .orderId(taskEntity.getOrderId())
                .vehicleId(taskEntity.getVehicleId())
                .dispatchType(taskEntity.getDispatchType())
                .status(taskEntity.getStatus())
                .failReasonCode(taskEntity.getFailReasonCode())
                .failReasonMsg(taskEntity.getFailReasonMsg())
                .assignTime(taskEntity.getAssignTime())
                .startTime(taskEntity.getStartTime())
                .finishTime(taskEntity.getFinishTime())
                .manualFlag(taskEntity.getManualFlag())
                .retryCount(taskEntity.getRetryCount())
                .remark(taskEntity.getRemark())
                .build();
    }

    @Override
    public List<DispatchTaskListItemResponse> listManualPendingTasks() {
        return dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                        .eq(DispatchTaskEntity::getDeleted, 0)
                        .eq(DispatchTaskEntity::getStatus, DispatchTaskStatus.MANUAL_PENDING.name())
                        .orderByDesc(DispatchTaskEntity::getUpdatedAt))
                .stream()
                .map(task -> DispatchTaskListItemResponse.builder()
                        .taskId(task.getId())
                        .taskNo(task.getTaskNo())
                        .orderId(task.getOrderId())
                        .vehicleId(task.getVehicleId())
                        .status(task.getStatus())
                        .failReasonCode(task.getFailReasonCode())
                        .failReasonMsg(task.getFailReasonMsg())
                        .createdAt(task.getCreatedAt())
                        .updatedAt(task.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    public DispatchSummaryResponse getSummary() {
        return DispatchSummaryResponse.builder()
                .pendingCount(countByStatus(DispatchTaskStatus.PENDING.name()))
                .assigningCount(countByStatus(DispatchTaskStatus.ASSIGNING.name()))
                .manualPendingCount(countByStatus(DispatchTaskStatus.MANUAL_PENDING.name()))
                .executingCount(countByStatus(DispatchTaskStatus.EXECUTING.name()))
                .failedCount(countByStatus(DispatchTaskStatus.FAILED.name()))
                .build();
    }

    private long countByStatus(String status) {
        Long count = dispatchTaskMapper.selectCount(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .eq(DispatchTaskEntity::getStatus, status));
        return count == null ? 0L : count;
    }

    private void moveToManualPending(DispatchTaskEntity taskEntity, String reasonCode, String reasonMessage) {
        String beforeStatus = taskEntity.getStatus();
        taskEntity.setStatus(DispatchTaskStatus.MANUAL_PENDING.name());
        taskEntity.setFailReasonCode(reasonCode);
        taskEntity.setFailReasonMsg(reasonMessage);
        taskEntity.setRetryCount(taskEntity.getRetryCount() == null ? 1 : taskEntity.getRetryCount() + 1);
        dispatchTaskMapper.updateById(taskEntity);
        operateLogService.record(taskEntity.getId(), "ENTER_MANUAL_PENDING", beforeStatus,
                DispatchTaskStatus.MANUAL_PENDING.name(), "SYSTEM", "system", "system", reasonMessage);
        dispatchExceptionService.recordException(taskEntity.getId(), taskEntity.getOrderId(), taskEntity.getVehicleId(),
                reasonCode, reasonMessage);
        eventPublisher.publish(DispatchEventType.TASK_MANUAL_PENDING, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));
    }

    private Map<String, Object> buildTaskPayload(DispatchTaskEntity taskEntity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskEntity.getId());
        payload.put("taskNo", taskEntity.getTaskNo());
        payload.put("orderId", taskEntity.getOrderId());
        payload.put("vehicleId", taskEntity.getVehicleId());
        payload.put("status", taskEntity.getStatus());
        payload.put("dispatchType", taskEntity.getDispatchType());
        payload.put("failReasonCode", taskEntity.getFailReasonCode());
        payload.put("failReasonMsg", taskEntity.getFailReasonMsg());
        payload.put("assignTime", taskEntity.getAssignTime());
        payload.put("manualFlag", taskEntity.getManualFlag());
        payload.put("retryCount", taskEntity.getRetryCount());
        return payload;
    }

    private String generateTaskNo() {
        return "TSK" + LocalDateTime.now().format(TASK_NO_TIME_FORMATTER)
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
