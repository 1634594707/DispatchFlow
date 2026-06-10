package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.dto.AdminDigitalTwinSimulateRequest;
import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.admin.service.DigitalTwinAdminService;
import com.fsd.admin.vo.AdminDigitalTwinSimulateResponse;
import com.fsd.admin.vo.AdminDigitalTwinSnapshotResponse;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dispatch.DispatchAssignResult;
import com.fsd.dispatch.dispatch.DispatchVehicleAssignService;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.DispatchExceptionListItemResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DigitalTwinAdminServiceImpl implements DigitalTwinAdminService {

    private final ParkPilotService parkPilotService;
    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final AdminParkScopeService adminParkScopeService;
    private final DispatchVehicleAssignService dispatchVehicleAssignService;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final OrderMapper orderMapper;

    public DigitalTwinAdminServiceImpl(ParkPilotService parkPilotService,
                                       DispatchAdminQueryService dispatchAdminQueryService,
                                       AdminParkScopeService adminParkScopeService,
                                       DispatchVehicleAssignService dispatchVehicleAssignService,
                                       DispatchTaskMapper dispatchTaskMapper,
                                       OrderMapper orderMapper) {
        this.parkPilotService = parkPilotService;
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.adminParkScopeService = adminParkScopeService;
        this.dispatchVehicleAssignService = dispatchVehicleAssignService;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public AdminDigitalTwinSnapshotResponse getSnapshot(Long parkId) {
        List<ParkVehicleSnapshotResponse> vehicles = parkPilotService.listVehicleSnapshots().stream()
                .filter(vehicle -> adminParkScopeService.matchesVehicleSnapshot(vehicle, parkId))
                .toList();
        List<DispatchTaskListItemResponse> tasks = dispatchAdminQueryService.listTasks().stream()
                .filter(task -> adminParkScopeService.matchesOrder(task.getOrderId(), parkId))
                .toList();
        List<DispatchExceptionListItemResponse> openExceptions = dispatchAdminQueryService.getInterventionQueue()
                .getOpenExceptions().stream()
                .filter(ex -> adminParkScopeService.matchesOrder(ex.getOrderId(), parkId))
                .toList();
        int pending = (int) tasks.stream()
                .filter(task -> "PENDING".equals(task.getStatus()) || "MANUAL_PENDING".equals(task.getStatus()))
                .count();
        int idleVehicleCount = (int) vehicles.stream()
                .filter(vehicle -> "IDLE".equals(vehicle.getDispatchStatus()))
                .count();
        int lowBatteryVehicleCount = (int) vehicles.stream()
                .filter(vehicle -> Boolean.TRUE.equals(vehicle.getLowBattery()))
                .count();
        return AdminDigitalTwinSnapshotResponse.builder()
                .layout(resolveLayout(parkId))
                .vehicles(vehicles)
                .pendingTaskCount(pending)
                .openExceptionCount(openExceptions.size())
                .idleVehicleCount(idleVehicleCount)
                .lowBatteryVehicleCount(lowBatteryVehicleCount)
                .build();
    }

    @Override
    public AdminDigitalTwinSimulateResponse simulate(AdminDigitalTwinSimulateRequest request) {
        Long parkId = request.getParkId();
        List<ParkVehicleSnapshotResponse> vehicles = parkPilotService.listVehicleSnapshots().stream()
                .filter(vehicle -> adminParkScopeService.matchesVehicleSnapshot(vehicle, parkId))
                .toList();
        int idleVehicles = (int) vehicles.stream()
                .filter(vehicle -> "IDLE".equals(vehicle.getDispatchStatus()) && "ONLINE".equals(vehicle.getOnlineStatus()))
                .count();
        int pendingTasks = request.getPendingTaskCount() == null ? 0 : request.getPendingTaskCount();
        String scenario = request.getScenario() == null ? "DISPATCH_PEAK" : request.getScenario().trim().toUpperCase();
        if ("TEXTILE_PEAK".equals(scenario)) {
            scenario = "DISPATCH_PEAK";
        }

        AdminDigitalTwinSimulateResponse engineResult = tryEngineSimulation(parkId, scenario, pendingTasks, idleVehicles, vehicles);
        if (engineResult != null) {
            return engineResult;
        }
        return estimateSimulation(scenario, parkId, pendingTasks, idleVehicles, vehicles);
    }

    private AdminDigitalTwinSimulateResponse tryEngineSimulation(Long parkId, String scenario, int pendingTasks,
                                                                 int idleVehicles,
                                                                 List<ParkVehicleSnapshotResponse> vehicles) {
        try {
            List<DispatchTaskEntity> pending = dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                            .eq(DispatchTaskEntity::getDeleted, 0)
                            .in(DispatchTaskEntity::getStatus,
                                    DispatchTaskStatus.PENDING.name(),
                                    DispatchTaskStatus.MANUAL_PENDING.name())
                            .orderByAsc(DispatchTaskEntity::getId))
                    .stream()
                    .filter(task -> adminParkScopeService.matchesOrder(task.getOrderId(), parkId))
                    .limit(Math.max(pendingTasks, 20))
                    .toList();
            if (pending.isEmpty() && pendingTasks > 0) {
                return null;
            }
            int success = 0;
            int fail = 0;
            double totalScore = 0;
            List<String> notes = new ArrayList<>();
            for (DispatchTaskEntity task : pending) {
                OrderEntity order = orderMapper.selectById(task.getOrderId());
                if (order == null) {
                    continue;
                }
                DispatchAssignResult result = dispatchVehicleAssignService.selectBestVehicle(order);
                if (result.isSuccess()) {
                    success++;
                    totalScore += result.getTotalScore() == null ? 0 : result.getTotalScore();
                } else {
                    fail++;
                    if (result.getFailReason() != null) {
                        notes.add(result.getFailReason().name() + ": " + result.getMessage());
                    }
                }
            }
            int sample = success + fail;
            if (sample == 0) {
                return null;
            }
            int estimatedMinutes = Math.max(3, (int) Math.ceil(sample * (success > 0 ? 4.0 * fail / sample + 3 : 8)));
            String summary;
            if ("CHARGING_SURGE".equals(scenario)) {
                long lowBattery = vehicles.stream().filter(v -> Boolean.TRUE.equals(v.getLowBattery())).count();
                summary = "引擎仿真·充电高峰：样本 " + sample + "，预计 " + estimatedMinutes + " 分钟";
                notes.add(0, "低电量车辆 " + lowBattery + " 台");
            } else if ("EXCEPTION_STORM".equals(scenario)) {
                summary = "引擎仿真·异常风暴：派车样本 " + sample + "，成功 " + success + " / 失败 " + fail;
            } else {
                summary = "引擎仿真·派车高峰：样本 " + sample + " 单，成功 " + success + "，预计 " + estimatedMinutes + " 分钟";
            }
            if (idleVehicles < success) {
                notes.add("空闲车不足：当前 " + idleVehicles + "，成功派车需 " + success);
            }
            notes.add("平均评分 " + String.format("%.1f", totalScore / Math.max(success, 1)));
            return AdminDigitalTwinSimulateResponse.builder()
                    .scenario(scenario)
                    .simulationMode("ENGINE")
                    .summary(summary)
                    .estimatedMinutes(estimatedMinutes)
                    .recommendedVehicleCount(Math.max(1, success))
                    .notes(notes.stream().distinct().limit(6).toList())
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }

    private AdminDigitalTwinSimulateResponse estimateSimulation(String scenario, Long parkId, int pendingTasks,
                                                                int idleVehicles,
                                                                List<ParkVehicleSnapshotResponse> vehicles) {
        List<String> notes = new ArrayList<>();
        int recommendedVehicles = Math.max(1, (int) Math.ceil(pendingTasks * 0.8));
        int estimatedMinutes = Math.max(3, pendingTasks * 4);
        String summary;
        if ("CHARGING_SURGE".equals(scenario)) {
            long lowBattery = vehicles.stream().filter(v -> Boolean.TRUE.equals(v.getLowBattery())).count();
            recommendedVehicles = (int) Math.max(1, lowBattery);
            estimatedMinutes = (int) Math.max(10, lowBattery * 15);
            summary = "估算仿真·充电高峰：预计 " + estimatedMinutes + " 分钟内缓解低电量";
            notes.add("低电量车辆 " + lowBattery + " 台");
        } else if ("EXCEPTION_STORM".equals(scenario)) {
            int openExceptionCount = (int) dispatchAdminQueryService.getInterventionQueue().getOpenExceptions().stream()
                    .filter(ex -> adminParkScopeService.matchesOrder(ex.getOrderId(), parkId))
                    .count();
            estimatedMinutes = Math.max(5, openExceptionCount * 6);
            summary = "估算仿真·异常风暴：预计 " + estimatedMinutes + " 分钟完成介入";
            notes.add("OPEN 异常 " + openExceptionCount + " 条");
        } else {
            if (idleVehicles < recommendedVehicles) {
                notes.add("空闲车辆不足：当前 " + idleVehicles + "，建议 " + recommendedVehicles);
                estimatedMinutes += (recommendedVehicles - idleVehicles) * 8;
            } else {
                notes.add("空闲车辆充足：当前 " + idleVehicles);
            }
            summary = "估算仿真·派车高峰：预计 " + estimatedMinutes + " 分钟消化 " + pendingTasks + " 个待派任务";
            notes.add("按每车 4 分钟/单估算，仅供预评估");
        }
        return AdminDigitalTwinSimulateResponse.builder()
                .scenario(scenario)
                .simulationMode("ESTIMATE")
                .summary(summary)
                .estimatedMinutes(estimatedMinutes)
                .recommendedVehicleCount(recommendedVehicles)
                .notes(notes)
                .build();
    }

    private ParkLayoutResponse resolveLayout(Long parkId) {
        if (parkId != null) {
            try {
                return parkPilotService.getLayout(parkId);
            } catch (BusinessException ex) {
                if (!"PARK_NOT_FOUND".equals(ex.getCode())) {
                    throw ex;
                }
            }
        }
        return parkPilotService.getLayout();
    }
}
