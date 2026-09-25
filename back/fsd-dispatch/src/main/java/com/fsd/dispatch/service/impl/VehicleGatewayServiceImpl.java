package com.fsd.dispatch.service.impl;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.fleet.FleetAdapterRegistry;
import com.fsd.dispatch.fleet.real.RealFleetAdapter;
import com.fsd.dispatch.infra.VehicleTelemetryIdempotencyService;
import com.fsd.dispatch.geo.ParkGeoTransformService;
import com.fsd.dispatch.service.VehicleGatewayService;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleReportService;
import com.fsd.vehicle.service.VehicleService;
import com.fsd.vehicle.vo.VehicleReportResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleGatewayServiceImpl implements VehicleGatewayService {

    private final VehicleService vehicleService;
    private final VehicleReportService vehicleReportService;
    private final FleetAdapterRegistry fleetAdapterRegistry;
    private final VehicleTelemetryIdempotencyService telemetryIdempotencyService;
    private final ParkGeoTransformService parkGeoTransformService;

    public VehicleGatewayServiceImpl(VehicleService vehicleService,
                                     VehicleReportService vehicleReportService,
                                     FleetAdapterRegistry fleetAdapterRegistry,
                                     VehicleTelemetryIdempotencyService telemetryIdempotencyService,
                                     ParkGeoTransformService parkGeoTransformService) {
        this.vehicleService = vehicleService;
        this.vehicleReportService = vehicleReportService;
        this.fleetAdapterRegistry = fleetAdapterRegistry;
        this.telemetryIdempotencyService = telemetryIdempotencyService;
        this.parkGeoTransformService = parkGeoTransformService;
    }

    @Override
    @Transactional
    public void ingestTelemetry(VehicleTelemetryRequest request) {
        if (!telemetryIdempotencyService.markIfFirstTelemetry(request)) {
            return;
        }
        VehicleEntity vehicle = vehicleService.getByVehicleCode(request.getVehicleCode());
        assertRealVehicle(vehicle);
        RealFleetAdapter adapter = fleetAdapterRegistry.require(VehicleLinkMode.REAL, RealFleetAdapter.class);
        if (!adapter.ingestTelemetry(vehicle, request)) {
            return;
        }
        vehicle.setBatteryLevel(request.getSoc());
        BigDecimal[] gcj = resolveGcj02(request);
        vehicle.setCurrentLongitude(gcj[0]);
        vehicle.setCurrentLatitude(gcj[1]);
        vehicle.setLastReportTime(request.getReportTime());
        vehicleService.updateSnapshot(buildSnapshotRequest(vehicle, request, gcj[0], gcj[1]));
    }

    /**
     * 真车上报的坐标契约（§7.2 定约，此前两条链路互斥）：
     * {@code longitude/latitude} 即车端 GPS 的 <b>GCJ-02</b>，直接透传入库；缺失时按 DTO 自述
     * 用园区坐标变换从 {@code x/y}（schematic 像素）反算。两者都拿不到就拒绝入库。
     *
     * <p>为什么宁可报错：把像素当经纬度写进 {@code current_longitude/latitude} 不会抛异常，
     * 只会让车在地图上穿墙、并让评分用的距离当场变成垃圾值 —— 静默改错比拒收贵得多。
     * 也不再在下游做 WGS-84→GCJ-02：那等于假定上报是 WGS-84，与 DTO 的"已是 GCJ-02"互斥。
     */
    private BigDecimal[] resolveGcj02(VehicleTelemetryRequest request) {
        if (request.getLongitude() != null && request.getLatitude() != null) {
            return new BigDecimal[]{request.getLongitude(), request.getLatitude()};
        }
        return parkGeoTransformService.toGcj02(request.getX(), request.getY())
                .map(point -> new BigDecimal[]{point.longitude(), point.latitude()})
                .orElseThrow(() -> new BusinessException("VEHICLE_GEO_UNRESOLVED",
                        "Real vehicle telemetry must carry GCJ-02 longitude/latitude, or the park"
                                + " coordinate transform must be enabled to derive them from x/y"));
    }

    @Override
    @Transactional
    public VehicleReportResponse handleReport(VehicleReportRequest request) {
        VehicleEntity vehicle = vehicleService.getByVehicleCode(request.getVehicleCode());
        assertRealVehicle(vehicle);
        return vehicleReportService.handleReport(request);
    }

    private void assertRealVehicle(VehicleEntity vehicle) {
        String linkMode = vehicle.getLinkMode() == null || vehicle.getLinkMode().isBlank()
                ? VehicleLinkMode.SIM.name()
                : vehicle.getLinkMode();
        if (!VehicleLinkMode.REAL.name().equals(linkMode)) {
            throw new BusinessException("VEHICLE_NOT_REAL", "Vehicle is not configured for real gateway access");
        }
    }

    private VehicleReportRequest buildSnapshotRequest(VehicleEntity vehicle, VehicleTelemetryRequest request,
                                                      BigDecimal longitude, BigDecimal latitude) {
        VehicleReportRequest snapshot = new VehicleReportRequest();
        snapshot.setVehicleCode(vehicle.getVehicleCode());
        snapshot.setOnlineStatus(vehicle.getOnlineStatus());
        snapshot.setDispatchStatus(vehicle.getDispatchStatus());
        snapshot.setLatitude(latitude);
        snapshot.setLongitude(longitude);
        snapshot.setBatteryLevel(request.getSoc());
        snapshot.setReportTime(request.getReportTime());
        snapshot.setReportType("TELEMETRY");
        return snapshot;
    }
}
