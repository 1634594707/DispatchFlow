package com.fsd.dispatch.geo;

import com.fsd.dispatch.config.AmapProperties;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.amap.AmapLogisticsDistanceService;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 派单距离估算：园区路网为主，可选叠加高德物流矩阵（M4）。
 * 公开道路距离不参与 {@code isReachable}，仅影响评分排序。
 */
@Service
public class DispatchGeoDistanceService {

    private final FleetGeoResolver fleetGeoResolver;
    private final FleetRuntimeService fleetRuntimeService;
    private final AmapLogisticsDistanceService amapLogisticsDistanceService;
    private final AmapProperties amapProperties;
    private final ParkMapper parkMapper;

    public DispatchGeoDistanceService(FleetGeoResolver fleetGeoResolver,
                                      FleetRuntimeService fleetRuntimeService,
                                      AmapLogisticsDistanceService amapLogisticsDistanceService,
                                      AmapProperties amapProperties,
                                      ParkMapper parkMapper) {
        this.fleetGeoResolver = fleetGeoResolver;
        this.fleetRuntimeService = fleetRuntimeService;
        this.amapLogisticsDistanceService = amapLogisticsDistanceService;
        this.amapProperties = amapProperties;
        this.parkMapper = parkMapper;
    }

    public boolean isGeoBlendEnabled() {
        return amapLogisticsDistanceService.isAvailable();
    }

    public Optional<GeoPoint> resolveVehicleGeo(VehicleEntity vehicle) {
        return fleetRuntimeService.get(vehicle.getId())
                .flatMap(runtime -> fleetGeoResolver.resolve(
                        runtime.getX() != null ? runtime.getX() : vehicle.getCurrentLongitude(),
                        runtime.getY() != null ? runtime.getY() : vehicle.getCurrentLatitude(),
                        runtime.getLongitude(),
                        runtime.getLatitude()))
                .or(() -> fleetGeoResolver.resolve(
                        vehicle.getCurrentLongitude(),
                        vehicle.getCurrentLatitude(),
                        null,
                        null));
    }

    public Optional<GeoPoint> resolveStationGeo(ParkStationResponse station) {
        if (station.getCoordLng() != null && station.getCoordLat() != null) {
            return Optional.of(new GeoPoint(station.getCoordLng(), station.getCoordLat()));
        }
        return amapLogisticsDistanceService.toGcj02(station.getX(), station.getY());
    }

    /**
     * 对候选车辆批量应用 N-1 距离混合（索引与 candidates 对齐）。
     */
    public List<Double> applyGeoBlend(List<VehicleEntity> candidates,
                                      ParkStationResponse pickup,
                                      List<Double> parkDistancesPx) {
        if (!isGeoBlendEnabled() || candidates.isEmpty()) {
            return parkDistancesPx;
        }
        Optional<GeoPoint> dest = resolveStationGeo(pickup);
        if (dest.isEmpty()) {
            return parkDistancesPx;
        }
        List<GeoPoint> origins = new ArrayList<>(candidates.size());
        for (VehicleEntity vehicle : candidates) {
            origins.add(resolveVehicleGeo(vehicle).orElse(null));
        }
        if (origins.stream().allMatch(point -> point == null)) {
            return parkDistancesPx;
        }
        List<GeoPoint> safeOrigins = new ArrayList<>(origins.size());
        for (GeoPoint point : origins) {
            safeOrigins.add(point != null ? point : dest.get());
        }
        double blendWeight = amapProperties.getLogistics().getBlendWeight();
        Map<Integer, Double> blended = amapLogisticsDistanceService.blendDistances(
                safeOrigins, dest.get(), parkDistancesPx, blendWeight);
        List<Double> result = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            result.add(blended.getOrDefault(i, parkDistancesPx.get(i)));
        }
        return result;
    }

    /**
     * 计算跨园区配送距离
     * @param fromParkId 起始园区ID
     * @param toParkId 目标园区ID
     * @return 距离（米），如果不可达返回 Double.MAX_VALUE
     */
    public double calculateCrossParkDistance(Long fromParkId, Long toParkId) {
        if (fromParkId == null || toParkId == null) {
            return Double.MAX_VALUE;
        }
        ParkEntity fromPark = parkMapper.selectById(fromParkId);
        ParkEntity toPark = parkMapper.selectById(toParkId);
        if (fromPark == null || toPark == null
                || fromPark.getCenterLng() == null || fromPark.getCenterLat() == null
                || toPark.getCenterLng() == null || toPark.getCenterLat() == null) {
            return Double.MAX_VALUE;
        }
        GeoPoint from = new GeoPoint(fromPark.getCenterLng(), fromPark.getCenterLat());
        GeoPoint to = new GeoPoint(toPark.getCenterLng(), toPark.getCenterLat());
        return GeoPolygonUtils.haversineMeters(from, to);
    }
}
