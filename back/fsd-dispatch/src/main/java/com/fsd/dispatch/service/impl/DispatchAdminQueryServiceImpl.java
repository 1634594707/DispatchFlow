package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.DispatchFleetMetricsResponse;
import com.fsd.dispatch.vo.DispatchWorkbenchResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.dispatch.vo.DispatchExceptionListItemResponse;
import com.fsd.dispatch.vo.DispatchInterventionQueueResponse;
import com.fsd.dispatch.vo.DispatchOpenExceptionBrief;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DispatchAdminQueryServiceImpl implements DispatchAdminQueryService {

    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchExceptionRecordMapper exceptionRecordMapper;
    private final DispatchTaskService dispatchTaskService;
    private final DispatchExceptionService dispatchExceptionService;
    private final ParkPilotService parkPilotService;
    private final FleetRuntimeService fleetRuntimeService;
    private final FleetChargePolicy fleetChargePolicy;

    public DispatchAdminQueryServiceImpl(DispatchTaskMapper dispatchTaskMapper,
                                         DispatchExceptionRecordMapper exceptionRecordMapper,
                                         DispatchTaskService dispatchTaskService,
                                         DispatchExceptionService dispatchExceptionService,
                                         ParkPilotService parkPilotService,
                                         FleetRuntimeService fleetRuntimeService,
                                         FleetChargePolicy fleetChargePolicy) {
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.dispatchTaskService = dispatchTaskService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.parkPilotService = parkPilotService;
        this.fleetRuntimeService = fleetRuntimeService;
        this.fleetChargePolicy = fleetChargePolicy;
    }

    @Override
    public List<DispatchTaskListItemResponse> listTasks() {
        List<DispatchTaskEntity> tasks = dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .orderByDesc(DispatchTaskEntity::getCreatedAt));
        Map<Long, List<DispatchOpenExceptionBrief>> openByTaskId = groupOpenExceptionsByTaskId();
        return tasks.stream()
                .map(task -> enrichTaskListItem(toTaskListItem(task), openByTaskId.getOrDefault(task.getId(), List.of())))
                .toList();
    }

    @Override
    public DispatchTaskDetailResponse getTaskDetail(Long taskId) {
        DispatchTaskDetailResponse detail = dispatchTaskService.getTaskDetail(taskId);
        List<DispatchOpenExceptionBrief> openExceptions = dispatchExceptionService.listOpenExceptionsByTaskId(taskId)
                .stream()
                .map(this::toOpenExceptionBrief)
                .toList();
        detail.setOpenExceptionCount(openExceptions.size());
        detail.setOpenExceptions(openExceptions);
        return detail;
    }

    @Override
    public List<DispatchExceptionListItemResponse> listExceptions() {
        List<DispatchExceptionRecordEntity> exceptions = exceptionRecordMapper.selectList(
                new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                        .orderByDesc(DispatchExceptionRecordEntity::getOccurTime));
        Map<Long, DispatchTaskEntity> taskById = loadTasksByIds(exceptions.stream()
                .map(DispatchExceptionRecordEntity::getTaskId)
                .filter(taskId -> taskId != null)
                .distinct()
                .toList());
        return exceptions.stream()
                .map(exception -> toExceptionListItem(exception, taskById.get(exception.getTaskId())))
                .toList();
    }

    @Override
    public DispatchInterventionQueueResponse getInterventionQueue() {
        List<DispatchTaskListItemResponse> pendingTasks = dispatchTaskService.listPendingTasks();
        List<DispatchTaskListItemResponse> manualPendingTasks = dispatchTaskService.listManualPendingTasks();
        Map<Long, List<DispatchOpenExceptionBrief>> openByTaskId = groupOpenExceptionsByTaskId();

        List<DispatchTaskListItemResponse> enrichedPending = pendingTasks.stream()
                .map(task -> enrichTaskListItem(task, openByTaskId.getOrDefault(task.getTaskId(), List.of())))
                .toList();
        List<DispatchTaskListItemResponse> enrichedManualPending = manualPendingTasks.stream()
                .map(task -> enrichTaskListItem(task, openByTaskId.getOrDefault(task.getTaskId(), List.of())))
                .toList();

        List<DispatchExceptionListItemResponse> openExceptions = dispatchExceptionService.listOpenExceptions().stream()
                .map(exception -> toExceptionListItem(exception, loadTask(exception.getTaskId())))
                .toList();

        return DispatchInterventionQueueResponse.builder()
                .pendingCount(enrichedPending.size())
                .manualPendingCount(enrichedManualPending.size())
                .openExceptionCount(openExceptions.size())
                .pendingTasks(enrichedPending)
                .manualPendingTasks(enrichedManualPending)
                .openExceptions(openExceptions)
                .build();
    }

    @Override
    public DispatchSummaryResponse getSummary() {
        return dispatchTaskService.getSummary();
    }

    @Override
    public DispatchWorkbenchResponse getWorkbench(Long parkId) {
        DispatchInterventionQueueResponse intervention = getInterventionQueue();
        List<ParkVehicleSnapshotResponse> vehicles = parkPilotService.listVehicleSnapshots();
        DispatchFleetMetricsResponse fleetMetrics = buildFleetMetrics(vehicles);
        return DispatchWorkbenchResponse.builder()
                .intervention(intervention)
                .fleetMetrics(fleetMetrics)
                .parkLayout(parkId == null ? parkPilotService.getLayout() : parkPilotService.getLayout(parkId))
                .vehicles(vehicles)
                .build();
    }

    private DispatchFleetMetricsResponse buildFleetMetrics(List<ParkVehicleSnapshotResponse> vehicles) {
        int assignable = 0;
        int pluggedStandby = 0;
        int charging = 0;
        int online = 0;
        for (ParkVehicleSnapshotResponse vehicle : vehicles) {
            if ("ONLINE".equals(vehicle.getOnlineStatus())) {
                online++;
            }
            if ("ONLINE".equals(vehicle.getOnlineStatus()) && "IDLE".equals(vehicle.getDispatchStatus())) {
                Integer battery = vehicle.getBatteryLevel();
                if (battery != null && fleetChargePolicy.isAssignable(
                        snapshotAsVehicle(vehicle.getVehicleId(), battery))) {
                    assignable++;
                }
            }
            var runtime = fleetRuntimeService.get(vehicle.getVehicleId()).orElse(null);
            if (runtime != null) {
                if (Boolean.TRUE.equals(runtime.getPluggedIn())
                        && "STANDBY".equals(runtime.getRuntimeStage())) {
                    pluggedStandby++;
                }
                if (fleetChargePolicy.isActivelyCharging(runtime.getRuntimeStage())) {
                    charging++;
                }
            } else if (Boolean.TRUE.equals(vehicle.getCharging())) {
                charging++;
            }
        }
        return DispatchFleetMetricsResponse.builder()
                .assignableVehicleCount(assignable)
                .pluggedStandbyCount(pluggedStandby)
                .chargingCount(charging)
                .onlineVehicleCount(online)
                .build();
    }

    private com.fsd.vehicle.entity.VehicleEntity snapshotAsVehicle(Long vehicleId, int battery) {
        com.fsd.vehicle.entity.VehicleEntity entity = new com.fsd.vehicle.entity.VehicleEntity();
        entity.setId(vehicleId);
        entity.setBatteryLevel(battery);
        return entity;
    }

    private Map<Long, List<DispatchOpenExceptionBrief>> groupOpenExceptionsByTaskId() {
        List<DispatchExceptionRecordEntity> openExceptions = dispatchExceptionService.listOpenExceptions();
        Map<Long, List<DispatchOpenExceptionBrief>> grouped = new LinkedHashMap<>();
        for (DispatchExceptionRecordEntity exception : openExceptions) {
            if (exception.getTaskId() == null) {
                continue;
            }
            grouped.computeIfAbsent(exception.getTaskId(), ignored -> new ArrayList<>())
                    .add(toOpenExceptionBrief(exception));
        }
        grouped.values().forEach(list -> list.sort(Comparator.comparing(DispatchOpenExceptionBrief::getOccurTime,
                Comparator.nullsLast(Comparator.reverseOrder()))));
        return grouped;
    }

    private Map<Long, DispatchTaskEntity> loadTasksByIds(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                        .in(DispatchTaskEntity::getId, taskIds)
                        .eq(DispatchTaskEntity::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(DispatchTaskEntity::getId, Function.identity(), (left, right) -> left));
    }

    private DispatchTaskEntity loadTask(Long taskId) {
        if (taskId == null) {
            return null;
        }
        return dispatchTaskMapper.selectOne(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getId, taskId)
                .eq(DispatchTaskEntity::getDeleted, 0));
    }

    private DispatchTaskListItemResponse toTaskListItem(DispatchTaskEntity task) {
        return DispatchTaskListItemResponse.builder()
                .taskId(task.getId())
                .taskNo(task.getTaskNo())
                .orderId(task.getOrderId())
                .vehicleId(task.getVehicleId())
                .status(task.getStatus())
                .failReasonCode(task.getFailReasonCode())
                .failReasonMsg(task.getFailReasonMsg())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private DispatchTaskListItemResponse enrichTaskListItem(DispatchTaskListItemResponse task,
                                                            List<DispatchOpenExceptionBrief> openExceptions) {
        task.setOpenExceptions(openExceptions);
        task.setOpenExceptionCount(openExceptions.size());
        task.setPrimaryOpenException(openExceptions.isEmpty() ? null : openExceptions.getFirst());
        return task;
    }

    private DispatchOpenExceptionBrief toOpenExceptionBrief(DispatchExceptionRecordEntity exception) {
        return DispatchOpenExceptionBrief.builder()
                .exceptionId(exception.getId())
                .exceptionType(exception.getExceptionType())
                .exceptionMsg(exception.getExceptionMsg())
                .severity(exception.getSeverity())
                .exceptionStatus(exception.getExceptionStatus())
                .occurTime(exception.getOccurTime())
                .build();
    }

    private DispatchExceptionListItemResponse toExceptionListItem(DispatchExceptionRecordEntity exception,
                                                                  DispatchTaskEntity task) {
        return DispatchExceptionListItemResponse.builder()
                .id(exception.getId())
                .taskId(exception.getTaskId())
                .taskNo(task == null ? null : task.getTaskNo())
                .taskStatus(task == null ? null : task.getStatus())
                .taskFailReasonCode(task == null ? null : task.getFailReasonCode())
                .taskFailReasonMsg(task == null ? null : task.getFailReasonMsg())
                .orderId(exception.getOrderId())
                .vehicleId(exception.getVehicleId())
                .exceptionType(exception.getExceptionType())
                .exceptionStatus(exception.getExceptionStatus())
                .exceptionMsg(exception.getExceptionMsg())
                .severity(exception.getSeverity())
                .resolveAction(exception.getResolveAction())
                .occurTime(exception.getOccurTime())
                .resolvedTime(exception.getResolvedTime())
                .resolverId(exception.getResolverId())
                .resolveRemark(exception.getResolveRemark())
                .createdAt(exception.getCreatedAt())
                .updatedAt(exception.getUpdatedAt())
                .build();
    }
}
