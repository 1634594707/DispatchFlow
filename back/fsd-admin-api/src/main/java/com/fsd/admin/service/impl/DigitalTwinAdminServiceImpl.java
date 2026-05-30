package com.fsd.admin.service.impl;

import com.fsd.admin.dto.AdminDigitalTwinSimulateRequest;
import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.admin.service.DigitalTwinAdminService;
import com.fsd.admin.vo.AdminDigitalTwinSimulateResponse;
import com.fsd.admin.vo.AdminDigitalTwinSnapshotResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.DispatchExceptionListItemResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DigitalTwinAdminServiceImpl implements DigitalTwinAdminService {

    private final ParkPilotService parkPilotService;
    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final AdminParkScopeService adminParkScopeService;

    public DigitalTwinAdminServiceImpl(ParkPilotService parkPilotService,
                                       DispatchAdminQueryService dispatchAdminQueryService,
                                       AdminParkScopeService adminParkScopeService) {
        this.parkPilotService = parkPilotService;
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.adminParkScopeService = adminParkScopeService;
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
        List<String> notes = new ArrayList<>();
        int recommendedVehicles = Math.max(1, (int) Math.ceil(pendingTasks * 0.8));
        int estimatedMinutes = Math.max(3, pendingTasks * 4);
        String summary;
        if ("CHARGING_SURGE".equals(scenario)) {
            long lowBattery = vehicles.stream().filter(v -> Boolean.TRUE.equals(v.getLowBattery())).count();
            recommendedVehicles = (int) Math.max(1, lowBattery);
            estimatedMinutes = (int) Math.max(10, lowBattery * 15);
            summary = "充电高峰仿真：预计 " + estimatedMinutes + " 分钟内可缓解低电量压力";
            notes.add("低电量车辆 " + lowBattery + " 台");
            notes.add("建议优先引导至空闲充电桩");
        } else if ("EXCEPTION_STORM".equals(scenario)) {
            int openExceptionCount = (int) dispatchAdminQueryService.getInterventionQueue().getOpenExceptions().stream()
                    .filter(ex -> adminParkScopeService.matchesOrder(ex.getOrderId(), parkId))
                    .count();
            estimatedMinutes = Math.max(5, openExceptionCount * 6);
            summary = "异常风暴仿真：预计 " + estimatedMinutes + " 分钟完成人工介入";
            notes.add("OPEN 异常 " + openExceptionCount + " 条");
            notes.add("建议启用批量改派与优先级提升");
        } else {
            if (idleVehicles < recommendedVehicles) {
                notes.add("空闲车辆不足：当前 " + idleVehicles + "，建议 " + recommendedVehicles);
                estimatedMinutes += (recommendedVehicles - idleVehicles) * 8;
            } else {
                notes.add("空闲车辆充足：当前 " + idleVehicles);
            }
            summary = "派车高峰仿真：预计 " + estimatedMinutes + " 分钟消化 " + pendingTasks + " 个待派任务";
            notes.add("按每车 4 分钟/单估算，仅供预评估");
        }
        if (!StringUtils.hasText(summary)) {
            summary = "仿真完成";
        }
        return AdminDigitalTwinSimulateResponse.builder()
                .scenario(scenario)
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
