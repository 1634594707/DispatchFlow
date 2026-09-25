package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.fleet.FleetAdapterRegistry;
import com.fsd.dispatch.fleet.real.RealFleetAdapter;
import com.fsd.dispatch.geo.ParkGeoTransformService;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.infra.VehicleTelemetryIdempotencyService;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleReportService;
import com.fsd.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 真车上报的坐标契约（路线图 §7.2 定约，实测缺陷见《已完成工作记录》§13.49）。
 *
 * <p>钉三件事：车端 GPS 的 GCJ-02 必须**原样**入库（旧的 vehicle 侧再转一次 WGS→GCJ 会把位置挪走
 * 上百米）；缺 GPS 时才允许由园区像素反算；两条都不可得时**拒收**，而不是把 {@code x/y} 当经纬度落库。
 */
class VehicleGatewayServiceImplTest {

    private static final BigDecimal GCJ_LNG = new BigDecimal("121.080681");
    private static final BigDecimal GCJ_LAT = new BigDecimal("31.960337");
    private static final BigDecimal PIXEL_X = new BigDecimal("668.437");
    private static final BigDecimal PIXEL_Y = new BigDecimal("624.45");

    private VehicleService vehicleService;
    private FleetAdapterRegistry fleetAdapterRegistry;
    private ParkGeoTransformService parkGeoTransformService;
    private VehicleGatewayServiceImpl gateway;
    private VehicleEntity vehicle;

    @BeforeEach
    void setUp() {
        vehicleService = mock(VehicleService.class);
        fleetAdapterRegistry = mock(FleetAdapterRegistry.class);
        parkGeoTransformService = mock(ParkGeoTransformService.class);
        VehicleTelemetryIdempotencyService idempotency = mock(VehicleTelemetryIdempotencyService.class);
        RealFleetAdapter adapter = mock(RealFleetAdapter.class);
        gateway = new VehicleGatewayServiceImpl(vehicleService, mock(VehicleReportService.class),
                fleetAdapterRegistry, idempotency, parkGeoTransformService);

        vehicle = new VehicleEntity();
        vehicle.setVehicleCode("ZJF-AV-01");
        vehicle.setLinkMode(VehicleLinkMode.REAL.name());
        when(idempotency.markIfFirstTelemetry(any())).thenReturn(true);
        when(vehicleService.getByVehicleCode("ZJF-AV-01")).thenReturn(vehicle);
        when(fleetAdapterRegistry.require(VehicleLinkMode.REAL, RealFleetAdapter.class)).thenReturn(adapter);
        when(adapter.ingestTelemetry(any(), any())).thenReturn(true);
    }

    @Test
    void onBoardGcj02IsStoredUntouched() {
        gateway.ingestTelemetry(telemetry(GCJ_LNG, GCJ_LAT));

        VehicleReportRequest snapshot = capturedSnapshot();
        assertEquals(0, GCJ_LNG.compareTo(snapshot.getLongitude()), "GCJ-02 不得被再次换算");
        assertEquals(0, GCJ_LAT.compareTo(snapshot.getLatitude()));
        assertEquals(0, GCJ_LNG.compareTo(vehicle.getCurrentLongitude()));
        assertEquals(0, GCJ_LAT.compareTo(vehicle.getCurrentLatitude()));
        verify(parkGeoTransformService, never()).toGcj02(any(), any());
    }

    @Test
    void missingGpsFallsBackToParkPixelTransform() {
        when(parkGeoTransformService.toGcj02(PIXEL_X, PIXEL_Y))
                .thenReturn(Optional.of(new GeoPoint(GCJ_LNG, GCJ_LAT)));

        gateway.ingestTelemetry(telemetry(null, null));

        VehicleReportRequest snapshot = capturedSnapshot();
        assertEquals(0, GCJ_LNG.compareTo(snapshot.getLongitude()));
        assertEquals(0, GCJ_LAT.compareTo(snapshot.getLatitude()));
    }

    /** 两条来源都不可得时必须拒收：把像素写进经纬度列不会报错，只会让车穿墙 + 评分距离变垃圾值。 */
    @Test
    void unresolvedPositionIsRejectedNotWrittenAsPixels() {
        when(parkGeoTransformService.toGcj02(any(), any())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gateway.ingestTelemetry(telemetry(null, null)));

        assertEquals("VEHICLE_GEO_UNRESOLVED", ex.getCode());
        verify(vehicleService, never()).updateSnapshot(any());
    }

    private VehicleReportRequest capturedSnapshot() {
        ArgumentCaptor<VehicleReportRequest> captor = ArgumentCaptor.forClass(VehicleReportRequest.class);
        verify(vehicleService).updateSnapshot(captor.capture());
        return captor.getValue();
    }

    private VehicleTelemetryRequest telemetry(BigDecimal longitude, BigDecimal latitude) {
        VehicleTelemetryRequest request = new VehicleTelemetryRequest();
        request.setVehicleCode("ZJF-AV-01");
        request.setRuntimeStage("MOVING");
        request.setSoc(80);
        request.setX(PIXEL_X);
        request.setY(PIXEL_Y);
        request.setLongitude(longitude);
        request.setLatitude(latitude);
        request.setReportTime(LocalDateTime.of(2026, 9, 22, 10, 0));
        request.setEventSeq(1L);
        return request;
    }
}
