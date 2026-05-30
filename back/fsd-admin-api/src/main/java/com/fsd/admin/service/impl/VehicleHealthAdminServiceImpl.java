package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.service.VehicleHealthAdminService;
import com.fsd.admin.vo.AdminVehicleHealthResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.entity.VehicleMaintenanceEntity;
import com.fsd.vehicle.mapper.VehicleMaintenanceMapper;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VehicleHealthAdminServiceImpl implements VehicleHealthAdminService {

    private final VehicleMapper vehicleMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchExceptionRecordMapper exceptionRecordMapper;
    private final VehicleMaintenanceMapper maintenanceMapper;

    public VehicleHealthAdminServiceImpl(VehicleMapper vehicleMapper,
                                         DispatchTaskMapper dispatchTaskMapper,
                                         DispatchExceptionRecordMapper exceptionRecordMapper,
                                         VehicleMaintenanceMapper maintenanceMapper) {
        this.vehicleMapper = vehicleMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.maintenanceMapper = maintenanceMapper;
    }

    @Override
    public AdminVehicleHealthResponse getHealth(Long vehicleId) {
        VehicleEntity vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || vehicle.getDeleted() != null && vehicle.getDeleted() != 0) {
            throw new BusinessException("VEHICLE_NOT_FOUND", "车辆不存在");
        }
        long openExceptions = exceptionRecordMapper.selectCount(new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                .eq(DispatchExceptionRecordEntity::getVehicleId, vehicleId)
                .eq(DispatchExceptionRecordEntity::getExceptionStatus, "OPEN"));
        long failedTasks = dispatchTaskMapper.selectCount(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getVehicleId, vehicleId)
                .eq(DispatchTaskEntity::getDeleted, 0)
                .eq(DispatchTaskEntity::getStatus, "FAILED"));
        long maintenanceCount = maintenanceMapper.selectCount(new LambdaQueryWrapper<VehicleMaintenanceEntity>()
                .eq(VehicleMaintenanceEntity::getVehicleId, vehicleId)
                .eq(VehicleMaintenanceEntity::getDeleted, 0));

        int score = 100;
        score -= Math.min(40, (int) openExceptions * 10);
        score -= Math.min(30, (int) failedTasks * 5);
        score -= Math.min(20, (int) maintenanceCount * 4);
        if (vehicle.getBatteryLevel() != null && vehicle.getBatteryLevel() < 20) {
            score -= 10;
        }
        score = Math.max(0, score);

        String level = score >= 80 ? "良好" : score >= 60 ? "一般" : score >= 40 ? "关注" : "风险";
        List<String> suggestions = new ArrayList<>();
        if (openExceptions > 0) {
            suggestions.add("存在 " + openExceptions + " 条未关闭异常，建议优先处置");
        }
        if (failedTasks > 2) {
            suggestions.add("近期失败任务较多，建议检查车辆传感器与调度策略");
        }
        if (maintenanceCount == 0) {
            suggestions.add("暂无维护记录，建议登记保养计划");
        }
        if (vehicle.getBatteryLevel() != null && vehicle.getBatteryLevel() < 25) {
            suggestions.add("电量偏低，建议安排充电");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("车辆运行状态良好");
        }

        return AdminVehicleHealthResponse.builder()
                .vehicleId(vehicleId)
                .vehicleCode(vehicle.getVehicleCode())
                .healthScore(score)
                .healthLevel(level)
                .openExceptionCount(openExceptions)
                .failedTaskCount(failedTasks)
                .maintenanceCount(maintenanceCount)
                .suggestions(suggestions)
                .build();
    }
}
