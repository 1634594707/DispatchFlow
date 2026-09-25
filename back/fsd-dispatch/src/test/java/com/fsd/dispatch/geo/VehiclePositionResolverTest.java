package com.fsd.dispatch.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.ParkGeoTransformService.ParkPoint;
import com.fsd.vehicle.entity.VehicleEntity;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * §7.2 接缝：同一张表里 SIM 行存像素、REAL 行存 GCJ-02，{@code toPark/toGeo} 必须按<b>行</b>选对空间。
 */
class VehiclePositionResolverTest {

    private static VehicleEntity vehicle(String linkMode, BigDecimal lng, BigDecimal lat) {
        VehicleEntity v = new VehicleEntity();
        v.setLinkMode(linkMode);
        v.setCurrentLongitude(lng);
        v.setCurrentLatitude(lat);
        return v;
    }

    @Test
    void simulatedRowTreatsColumnAsParkAndDerivesGcjViaToGcj02() {
        ParkGeoTransformService t = mock(ParkGeoTransformService.class);
        when(t.toGcj02(any(), any()))
                .thenReturn(Optional.of(new GeoPoint(new BigDecimal("121.08"), new BigDecimal("31.96"))));
        VehiclePositionResolver r = new VehiclePositionResolver(t);
        VehicleEntity sim = vehicle(VehicleLinkMode.SIM.name(), new BigDecimal("668.437"), new BigDecimal("624.45"));

        ParkPoint park = r.toPark(sim).orElseThrow();
        assertEquals(0, new BigDecimal("668.437").compareTo(park.x()), "SIM 行：toPark 直取列值");
        assertEquals(0, new BigDecimal("624.45").compareTo(park.y()));
        assertEquals(VehicleLinkMode.isSimulated(null), r.storesSchematicXy(vehicle(null, BigDecimal.ONE, BigDecimal.ONE)),
                "历史空 linkMode 归入仿真（与写入侧同一条规则）");
        assertTrue(r.toGeo(sim).isPresent());
        verify(t).toGcj02(any(), any());
        verify(t, never()).fromGcj02(any(), any());
    }

    @Test
    void realRowTreatsColumnAsGcjAndDerivesParkViaFromGcj02() {
        ParkGeoTransformService t = mock(ParkGeoTransformService.class);
        when(t.fromGcj02(any(), any()))
                .thenReturn(Optional.of(new ParkPoint(new BigDecimal("668.4"), new BigDecimal("624.4"))));
        VehiclePositionResolver r = new VehiclePositionResolver(t);
        VehicleEntity real = vehicle(VehicleLinkMode.REAL.name(),
                new BigDecimal("121.080081"), new BigDecimal("31.960592"));

        GeoPoint geo = r.toGeo(real).orElseThrow();
        assertEquals(0, new BigDecimal("121.080081").compareTo(geo.longitude()), "REAL 行：toGeo 直取列值");
        assertEquals(0, new BigDecimal("31.960592").compareTo(geo.latitude()));
        assertTrue(r.toPark(real).isPresent());
        verify(t).fromGcj02(any(), any());
        verify(t, never()).toGcj02(any(), any());
    }

    /** 混合车队：两种行同时存在时不能互相污染——这是全局开关做不到的，也是它被废弃的原因。 */
    @Test
    void mixedFleetResolvesEachRowInItsOwnSpace() {
        ParkGeoTransformService t = mock(ParkGeoTransformService.class);
        when(t.toGcj02(any(), any()))
                .thenReturn(Optional.of(new GeoPoint(new BigDecimal("121.0"), new BigDecimal("31.0"))));
        when(t.fromGcj02(any(), any()))
                .thenReturn(Optional.of(new ParkPoint(new BigDecimal("10"), new BigDecimal("20"))));
        VehiclePositionResolver r = new VehiclePositionResolver(t);
        VehicleEntity sim = vehicle(VehicleLinkMode.SIM.name(), new BigDecimal("668"), new BigDecimal("624"));
        VehicleEntity real = vehicle(VehicleLinkMode.VDA5050.name(), new BigDecimal("121.08"), new BigDecimal("31.96"));

        assertEquals(0, new BigDecimal("668").compareTo(r.toPark(sim).orElseThrow().x()));
        assertEquals(0, new BigDecimal("10").compareTo(r.toPark(real).orElseThrow().x()));
        assertEquals(0, new BigDecimal("121.0").compareTo(r.toGeo(sim).orElseThrow().longitude()));
        assertEquals(0, new BigDecimal("121.08").compareTo(r.toGeo(real).orElseThrow().longitude()));
    }

    @Test
    void missingCoordinatesYieldEmptyRegardlessOfLinkMode() {
        ParkGeoTransformService t = mock(ParkGeoTransformService.class);
        VehiclePositionResolver r = new VehiclePositionResolver(t);
        VehicleEntity none = vehicle(VehicleLinkMode.REAL.name(), null, null);
        assertTrue(r.toPark(none).isEmpty() && r.toGeo(none).isEmpty());
        verify(t, never()).toGcj02(any(), any());
        verify(t, never()).fromGcj02(any(), any());
    }
}
