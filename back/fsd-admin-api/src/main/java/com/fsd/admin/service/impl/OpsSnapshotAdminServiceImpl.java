package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.service.OpsSnapshotAdminService;
import com.fsd.admin.service.VerticalAdminService;
import com.fsd.admin.vo.AdminHubOverviewResponse;
import com.fsd.admin.vo.AdminOpsClusterItem;
import com.fsd.admin.vo.AdminOpsSnapshotResponse;
import com.fsd.admin.vo.AdminOpsVehicleItem;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.geo.ParkGeoTransformService.ParkPoint;
import com.fsd.dispatch.geo.VehiclePositionResolver;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OpsSnapshotAdminServiceImpl implements OpsSnapshotAdminService {

    private final VehicleMapper vehicleMapper;
    private final VerticalAdminService verticalAdminService;
    private final FleetEnergyProperties fleetEnergyProperties;
    private final VehiclePositionResolver vehiclePositionResolver;

    public OpsSnapshotAdminServiceImpl(VehicleMapper vehicleMapper,
                                       VerticalAdminService verticalAdminService,
                                       FleetEnergyProperties fleetEnergyProperties,
                                       VehiclePositionResolver vehiclePositionResolver) {
        this.vehicleMapper = vehicleMapper;
        this.verticalAdminService = verticalAdminService;
        this.fleetEnergyProperties = fleetEnergyProperties;
        this.vehiclePositionResolver = vehiclePositionResolver;
    }

    @Override
    public AdminOpsSnapshotResponse getSnapshot(Long parkId) {
        List<VehicleEntity> vehicles = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0));
        Map<String, ClusterAcc> clusters = new HashMap<>();
        List<AdminOpsVehicleItem> offline = new ArrayList<>();
        for (VehicleEntity vehicle : vehicles) {
            if ("OFFLINE".equals(vehicle.getOnlineStatus())
                    && vehicle.getLastReportTime() != null
                    && Duration.between(vehicle.getLastReportTime(), LocalDateTime.now()).toMinutes() >= 5) {
                offline.add(AdminOpsVehicleItem.builder()
                        .vehicleId(vehicle.getId())
                        .vehicleCode(vehicle.getVehicleCode())
                        .soc(vehicle.getBatteryLevel())
                        .offlineMinutes(Duration.between(vehicle.getLastReportTime(), LocalDateTime.now()).toMinutes())
                        .build());
            }
            Integer soc = vehicle.getBatteryLevel();
            if (soc == null || soc > fleetEnergyProperties.getLowSocThreshold()) {
                continue;
            }
            // 热力分桶是像素刻度（/100），故统一取像素空间（§7.2）
            Optional<ParkPoint> parkPoint = vehiclePositionResolver.toPark(vehicle);
            if (parkPoint.isEmpty()) {
                continue;
            }
            double parkX = parkPoint.get().x().doubleValue();
            double parkY = parkPoint.get().y().doubleValue();
            int gridX = (int) (parkX / 100);
            int gridY = (int) (parkY / 100);
            String key = gridX + ":" + gridY;
            ClusterAcc acc = clusters.computeIfAbsent(key, k -> new ClusterAcc(k, parkX, parkY, soc));
            acc.count++;
            acc.minSoc = Math.min(acc.minSoc, soc);
        }
        AdminHubOverviewResponse hub = verticalAdminService.getHubOverview(parkId);
        List<AdminOpsClusterItem> clusterItems = clusters.values().stream()
                .map(acc -> AdminOpsClusterItem.builder()
                        .gridKey(acc.gridKey)
                        .vehicleCount(acc.count)
                        .centerX(acc.centerX)
                        .centerY(acc.centerY)
                        .minSoc(acc.minSoc)
                        .build())
                .toList();
        return AdminOpsSnapshotResponse.builder()
                .lowBatteryClusters(clusterItems)
                .offlineVehicles(offline)
                .hubQueuedTasks(hub.getQueuedTasks())
                .build();
    }

    private static class ClusterAcc {
        private final String gridKey;
        private final double centerX;
        private final double centerY;
        private int count;
        private int minSoc;

        private ClusterAcc(String gridKey, double centerX, double centerY, int minSoc) {
            this.gridKey = gridKey;
            this.centerX = centerX;
            this.centerY = centerY;
            this.count = 1;
            this.minSoc = minSoc;
        }
    }
}
