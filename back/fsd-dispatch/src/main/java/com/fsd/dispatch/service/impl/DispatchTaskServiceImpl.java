package com.fsd.dispatch.service.impl;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.fsd.common.enums.DispatchAssignFailReason;

import com.fsd.common.enums.DispatchTaskStatus;

import com.fsd.common.enums.VehicleDispatchStatus;

import com.fsd.common.exception.BusinessException;

import com.fsd.dispatch.dispatch.DispatchAssignResult;

import com.fsd.dispatch.dispatch.DispatchFailExplainSupport;

import com.fsd.dispatch.dispatch.DispatchVehicleAssignService;

import com.fsd.dispatch.dto.DispatchTaskCreateRequest;

import com.fsd.dispatch.dto.DispatchTaskManualAssignRequest;

import com.fsd.dispatch.entity.DispatchTaskEntity;

import com.fsd.dispatch.event.DispatchEventPublisher;

import com.fsd.dispatch.event.DispatchEventType;

import com.fsd.dispatch.infra.DispatchLockService;

import com.fsd.dispatch.mapper.DispatchTaskMapper;

import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchPauseControlService;

import com.fsd.dispatch.service.ParkingFacilityService;

import com.fsd.dispatch.service.DispatchTaskOperateLogService;

import com.fsd.dispatch.service.DispatchTaskService;

import com.fsd.dispatch.service.DispatchTaskStateService;

import com.fsd.dispatch.service.VehicleCommandService;

import com.fsd.dispatch.vo.DispatchSummaryResponse;

import com.fsd.dispatch.vo.DispatchTaskAssignResponse;

import com.fsd.dispatch.vo.DispatchTaskCreateResponse;

import com.fsd.dispatch.vo.DispatchTaskDetailResponse;

import com.fsd.dispatch.vo.DispatchTaskListItemResponse;

import com.fsd.order.entity.OrderEntity;

import com.fsd.order.service.OrderStateService;

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

    private final com.fsd.vehicle.service.VehicleService vehicleService;

    private final DispatchVehicleAssignService dispatchVehicleAssignService;

    private final ParkingFacilityService parkingFacilityService;

    private final DispatchLockService dispatchLockService;

    private final DispatchEventPublisher eventPublisher;

    private final VehicleCommandService vehicleCommandService;

    private final DispatchPauseControlService dispatchPauseControlService;



    public DispatchTaskServiceImpl(DispatchTaskMapper dispatchTaskMapper,

                                   DispatchTaskStateService dispatchTaskStateService,

                                   DispatchTaskOperateLogService operateLogService,

                                   DispatchExceptionService dispatchExceptionService,

                                   OrderStateService orderStateService,

                                   com.fsd.vehicle.service.VehicleService vehicleService,

                                   DispatchVehicleAssignService dispatchVehicleAssignService,

                                   ParkingFacilityService parkingFacilityService,

                                   DispatchLockService dispatchLockService,

                                   DispatchEventPublisher eventPublisher,

                                   VehicleCommandService vehicleCommandService,
                                   DispatchPauseControlService dispatchPauseControlService) {

        this.dispatchTaskMapper = dispatchTaskMapper;

        this.dispatchTaskStateService = dispatchTaskStateService;

        this.operateLogService = operateLogService;

        this.dispatchExceptionService = dispatchExceptionService;

        this.orderStateService = orderStateService;

        this.vehicleService = vehicleService;

        this.dispatchVehicleAssignService = dispatchVehicleAssignService;

        this.parkingFacilityService = parkingFacilityService;

        this.dispatchLockService = dispatchLockService;

        this.eventPublisher = eventPublisher;

        this.vehicleCommandService = vehicleCommandService;

        this.dispatchPauseControlService = dispatchPauseControlService;

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

        final String lockToken;
        try {
            lockToken = dispatchLockService.acquireTaskLock(taskId);
        } catch (BusinessException ex) {
            if ("DISPATCH_TASK_LOCKED".equals(ex.getCode())) {
                DispatchTaskEntity taskEntity = dispatchTaskStateService.getTask(taskId);
                return buildAssignFailureResponse(taskEntity, "CONFLICT", "任务正在处理中，请稍后重试");
            }
            throw ex;
        }

        try {

            DispatchTaskEntity taskEntity = dispatchTaskStateService.getTask(taskId);

            prepareStuckTaskForAutoAssignRetry(taskEntity);

            DispatchTaskAssignResponse statusBlocked = validateAutoAssignStatus(taskEntity);
            if (statusBlocked != null) {
                return statusBlocked;
            }

            String beforeStatus = taskEntity.getStatus();

            taskEntity.setStatus(DispatchTaskStatus.ASSIGNING.name());

            taskEntity.setRetryCount(taskEntity.getRetryCount() == null ? 0 : taskEntity.getRetryCount());

            dispatchTaskMapper.updateById(taskEntity);

            operateLogService.record(taskEntity.getId(), "AUTO_ASSIGN", beforeStatus, taskEntity.getStatus(),

                    "SYSTEM", "system", "system", "Auto assign started");



            OrderEntity orderEntity = orderStateService.getOrder(taskEntity.getOrderId());

            DispatchAssignResult assignResult;

            try {
                if (dispatchPauseControlService.isDispatchPaused(orderEntity.getParkId())) {
                    throw new BusinessException("DISPATCH_PAUSED", "当前园区已暂停新派单");
                }

                assignResult = dispatchVehicleAssignService.selectBestVehicle(orderEntity);

            } catch (BusinessException ex) {

                String code = mapBusinessExceptionToFailReason(ex);

                moveToManualPending(taskEntity, code, ex.getMessage());

                return buildAssignFailureResponse(taskEntity, code);

            }



            if (!assignResult.isSuccess()) {

                String code = assignResult.getFailReason() != null

                        ? assignResult.getFailReason().name()

                        : DispatchAssignFailReason.NO_VEHICLE.name();

                moveToManualPending(taskEntity, code, assignResult.getMessage());

                return buildAssignFailureResponse(taskEntity, code);

            }



            var vehicleEntity = assignResult.getVehicle();

            try {

                vehicleService.occupyVehicle(vehicleEntity.getId(), taskEntity.getId(), taskEntity.getOrderId());

                parkingFacilityService.releaseByVehicle(vehicleEntity.getId());

            } catch (BusinessException ex) {

                moveToManualPending(taskEntity, DispatchAssignFailReason.CONFLICT.name(), ex.getMessage());

                return buildAssignFailureResponse(taskEntity, DispatchAssignFailReason.CONFLICT.name());

            }



            taskEntity.setVehicleId(vehicleEntity.getId());

            taskEntity.setStatus(DispatchTaskStatus.ASSIGNED.name());

            taskEntity.setAssignTime(LocalDateTime.now());

            taskEntity.setFailReasonCode(null);

            taskEntity.setFailReasonMsg(null);

            dispatchTaskMapper.updateById(taskEntity);



            orderStateService.markDispatched(taskEntity.getOrderId(), taskEntity.getId());

            dispatchExceptionService.resolveOpenExceptionsForTask(taskEntity.getId(), "system", "Auto assign success");

            operateLogService.record(taskEntity.getId(), "AUTO_ASSIGN", DispatchTaskStatus.ASSIGNING.name(),

                    DispatchTaskStatus.ASSIGNED.name(), "SYSTEM", "system", "system", assignResult.getMessage());

            eventPublisher.publish(DispatchEventType.TASK_ASSIGNED, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));

            vehicleCommandService.issueDispatchCommandIfNeeded(vehicleEntity, taskEntity, orderEntity);



            return DispatchTaskAssignResponse.builder()

                    .taskId(taskEntity.getId())

                    .status(taskEntity.getStatus())

                    .vehicleId(vehicleEntity.getId())

                    .selectedVehicleCode(assignResult.getVehicleCode())

                    .assignScore(assignResult.getTotalScore())

                    .assignExplanation(assignResult.getMessage())

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

            parkingFacilityService.releaseByVehicle(request.getVehicleId());



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

            dispatchExceptionService.resolveOpenExceptionsForTask(taskEntity.getId(), request.getOperatorId(),

                    "Manual assign success");

            operateLogService.record(taskEntity.getId(), "MANUAL_ASSIGN", beforeStatus, DispatchTaskStatus.ASSIGNED.name(),

                    "DISPATCHER", request.getOperatorId(), request.getOperatorName(), request.getRemark());

            eventPublisher.publish(DispatchEventType.TASK_MANUAL_ASSIGNED, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));

            OrderEntity orderEntity = orderStateService.getOrder(taskEntity.getOrderId());

            vehicleCommandService.issueDispatchCommandIfNeeded(

                    vehicleService.getById(request.getVehicleId()), taskEntity, orderEntity);



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

    @Transactional

    public DispatchTaskAssignResponse cancelTask(Long taskId, String operatorId, String operatorName, String remark) {

        String lockToken = dispatchLockService.acquireTaskLock(taskId);

        try {

            DispatchTaskEntity taskEntity = dispatchTaskStateService.getTask(taskId);

            dispatchTaskStateService.assertCanCancel(taskEntity);

            String beforeStatus = taskEntity.getStatus();

            if (taskEntity.getVehicleId() != null) {

                vehicleService.releaseVehicle(taskEntity.getVehicleId(), VehicleDispatchStatus.IDLE.name());

                parkingFacilityService.releaseByVehicle(taskEntity.getVehicleId());

            }

            taskEntity.setStatus(DispatchTaskStatus.CANCELLED.name());

            taskEntity.setFinishTime(LocalDateTime.now());

            dispatchTaskMapper.updateById(taskEntity);

            orderStateService.markCancelled(taskEntity.getOrderId());

            operateLogService.record(taskEntity.getId(), "CANCEL_TASK", beforeStatus,

                    DispatchTaskStatus.CANCELLED.name(), "DISPATCHER", operatorId, operatorName, remark);

            eventPublisher.publish(DispatchEventType.TASK_CANCELLED, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));

            return DispatchTaskAssignResponse.builder()

                    .taskId(taskEntity.getId())

                    .status(taskEntity.getStatus())

                    .message("Task cancelled")

                    .build();

        } finally {

            dispatchLockService.releaseTaskLock(taskId, lockToken);

        }

    }



    @Override

    @Transactional

    public DispatchTaskAssignResponse unassignTask(Long taskId, String operatorId, String operatorName, String remark) {

        String lockToken = dispatchLockService.acquireTaskLock(taskId);

        try {

            DispatchTaskEntity taskEntity = dispatchTaskStateService.getTask(taskId);

            dispatchTaskStateService.assertCanReassign(taskEntity);
            String beforeStatus = taskEntity.getStatus();

            if (taskEntity.getVehicleId() != null) {

                vehicleService.releaseVehicle(taskEntity.getVehicleId(), VehicleDispatchStatus.IDLE.name());

                parkingFacilityService.releaseByVehicle(taskEntity.getVehicleId());

            }

            taskEntity.setVehicleId(null);

            taskEntity.setStatus(DispatchTaskStatus.PENDING.name());

            taskEntity.setAssignTime(null);

            taskEntity.setManualFlag(0);

            dispatchTaskMapper.updateById(taskEntity);

            operateLogService.record(taskEntity.getId(), "UNASSIGN_TASK", beforeStatus,

                    DispatchTaskStatus.PENDING.name(), "DISPATCHER", operatorId, operatorName, remark);

            eventPublisher.publish(DispatchEventType.TASK_CREATED, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));

            return DispatchTaskAssignResponse.builder()

                    .taskId(taskEntity.getId())

                    .status(taskEntity.getStatus())

                    .message("Task unassigned")

                    .build();

        } finally {

            dispatchLockService.releaseTaskLock(taskId, lockToken);

        }

    }



    @Override

    @Transactional

    public DispatchTaskAssignResponse reassignTask(Long taskId, DispatchTaskManualAssignRequest request) {

        String lockToken = dispatchLockService.acquireTaskLock(taskId);

        try {

            DispatchTaskEntity taskEntity = dispatchTaskStateService.getTask(taskId);

            dispatchTaskStateService.assertCanReassign(taskEntity);

            Long previousVehicleId = taskEntity.getVehicleId();

            if (previousVehicleId != null) {

                vehicleService.releaseVehicle(previousVehicleId, VehicleDispatchStatus.IDLE.name());

                parkingFacilityService.releaseByVehicle(previousVehicleId);

            }

            vehicleService.occupyVehicle(request.getVehicleId(), taskEntity.getId(), taskEntity.getOrderId());

            parkingFacilityService.releaseByVehicle(request.getVehicleId());

            String beforeStatus = taskEntity.getStatus();

            taskEntity.setVehicleId(request.getVehicleId());

            taskEntity.setAssignTime(LocalDateTime.now());

            taskEntity.setManualFlag(1);

            dispatchTaskMapper.updateById(taskEntity);

            operateLogService.record(taskEntity.getId(), "REASSIGN", beforeStatus, taskEntity.getStatus(),

                    "DISPATCHER", request.getOperatorId(), request.getOperatorName(), request.getRemark());

            OrderEntity orderEntity = orderStateService.getOrder(taskEntity.getOrderId());

            vehicleCommandService.issueDispatchCommandIfNeeded(

                    vehicleService.getById(request.getVehicleId()), taskEntity, orderEntity);

            return DispatchTaskAssignResponse.builder()

                    .taskId(taskEntity.getId())

                    .status(taskEntity.getStatus())

                    .vehicleId(taskEntity.getVehicleId())

                    .assignTime(taskEntity.getAssignTime())

                    .message("Reassign success")

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

        return listTasksByStatus(DispatchTaskStatus.MANUAL_PENDING.name());

    }



    @Override

    public List<DispatchTaskListItemResponse> listPendingTasks() {

        return listTasksByStatus(DispatchTaskStatus.PENDING.name());

    }



    private List<DispatchTaskListItemResponse> listTasksByStatus(String status) {

        return dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()

                        .eq(DispatchTaskEntity::getDeleted, 0)

                        .eq(DispatchTaskEntity::getStatus, status)

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

                reasonCode, reasonMessage, com.fsd.common.enums.ExceptionSeverity.WARN.name());

        eventPublisher.publish(DispatchEventType.TASK_MANUAL_PENDING, String.valueOf(taskEntity.getId()), buildTaskPayload(taskEntity));

    }



    /**
     * 演示/恢复：已派单或执行中但车辆已空闲时，回退到人工待处理以便再次自动派车。
     */
    private void prepareStuckTaskForAutoAssignRetry(DispatchTaskEntity taskEntity) {
        String status = taskEntity.getStatus();
        if (!DispatchTaskStatus.ASSIGNED.name().equals(status)
                && !DispatchTaskStatus.EXECUTING.name().equals(status)) {
            return;
        }
        Long vehicleId = taskEntity.getVehicleId();
        if (vehicleId != null) {
            try {
                vehicleService.releaseVehicle(vehicleId, VehicleDispatchStatus.IDLE.name());
            } catch (BusinessException ignored) {
                // 车辆可能已被释放
            }
            parkingFacilityService.releaseByVehicle(vehicleId);
        }
        taskEntity.setVehicleId(null);
        taskEntity.setStatus(DispatchTaskStatus.MANUAL_PENDING.name());
        taskEntity.setFailReasonCode(null);
        taskEntity.setFailReasonMsg(null);
        dispatchTaskMapper.updateById(taskEntity);
        try {
            orderStateService.revertToWaitingDispatch(taskEntity.getOrderId());
        } catch (BusinessException ignored) {
            // 订单可能已是待派状态
        }
        operateLogService.record(taskEntity.getId(), "RESET_FOR_AUTO_ASSIGN", status,
                DispatchTaskStatus.MANUAL_PENDING.name(), "SYSTEM", "system", "system",
                "Stuck task reset for auto-assign retry");
    }

    private DispatchTaskAssignResponse validateAutoAssignStatus(DispatchTaskEntity taskEntity) {
        try {
            dispatchTaskStateService.assertCanAutoAssign(taskEntity);
            return null;
        } catch (BusinessException ex) {
            if (!"DISPATCH_TASK_STATUS_INVALID".equals(ex.getCode())) {
                throw ex;
            }
            String status = taskEntity.getStatus();
            String hint = switch (status == null ? "" : status) {
                case "ASSIGNED", "EXECUTING" ->
                        "任务状态为「" + status + "」，请先取消任务或改派，不能再次自动派车";
                case "CANCELLED", "FAILED", "SUCCESS" ->
                        "任务已结束（" + status + "），请创建新订单或处理其他待派任务";
                default ->
                        "任务状态为「" + status + "」，当前不允许自动派车（仅支持待派单/人工待处理）";
            };
            return buildAssignFailureResponse(taskEntity, "INVALID_STATUS", hint);
        }
    }

    private DispatchTaskAssignResponse buildAssignFailureResponse(DispatchTaskEntity taskEntity, String failCode) {
        return buildAssignFailureResponse(taskEntity, failCode, taskEntity.getFailReasonMsg());
    }

    private DispatchTaskAssignResponse buildAssignFailureResponse(DispatchTaskEntity taskEntity, String failCode,
                                                                  String messageOverride) {

        var explained = DispatchFailExplainSupport.explain(failCode, messageOverride);

        return DispatchTaskAssignResponse.builder()

                .taskId(taskEntity.getId())

                .status(taskEntity.getStatus())

                .failReasonCode(failCode)

                .reasonCode(explained.reasonCode())

                .reasonMessage(explained.reasonMessage())

                .suggestions(explained.suggestions())

                .message(explained.reasonMessage())

                .build();

    }



    private String mapBusinessExceptionToFailReason(BusinessException ex) {

        if ("PARK_STATION_NOT_FOUND".equals(ex.getCode()) || "PARK_ROUTE_NOT_FOUND".equals(ex.getCode())) {

            return DispatchAssignFailReason.UNREACHABLE.name();

        }

        return DispatchAssignFailReason.NO_VEHICLE.name();

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


