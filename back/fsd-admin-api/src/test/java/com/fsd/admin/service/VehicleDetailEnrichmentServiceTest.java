package com.fsd.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RouteAuditEntity;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RouteAuditMapper;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 路线图 5.2：车辆详情路线上下文 —— 当前道路节点、路线 ID、地图版本、偏航距离、执行时间。
 */
class VehicleDetailEnrichmentServiceTest {

    private RouteAuditMapper routeAuditMapper;
    private RoadNodeMapper roadNodeMapper;
    private VehicleDetailEnrichmentService service;

    @BeforeEach
    void setUp() {
        routeAuditMapper = mock(RouteAuditMapper.class);
        roadNodeMapper = mock(RoadNodeMapper.class);
        service = new VehicleDetailEnrichmentService(routeAuditMapper, roadNodeMapper);
    }

    private VehicleAdminDetailResponse detail(Long parkId, String lng, String lat) {
        return VehicleAdminDetailResponse.builder()
                .vehicleId(7L)
                .parkId(parkId)
                .currentLongitude(lng == null ? null : new BigDecimal(lng))
                .currentLatitude(lat == null ? null : new BigDecimal(lat))
                .build();
    }

    private RouteAuditEntity audit(String routeId, String mapVersion, double deviation) {
        RouteAuditEntity entity = new RouteAuditEntity();
        entity.setVehicleId(7L);
        entity.setRouteId(routeId);
        entity.setMapVersionCode(mapVersion);
        entity.setDeviationMeters(BigDecimal.valueOf(deviation));
        entity.setExecutedAt(LocalDateTime.of(2026, 8, 24, 9, 0));
        entity.setDeleted(0);
        return entity;
    }

    @Test
    void shouldFillLatestRouteAuditContext() {
        VehicleAdminDetailResponse target = detail(1L, "121.100000", "31.920000");
        when(routeAuditMapper.selectOne(any())).thenReturn(audit("ROUTE-AB12", "V43", 12.5));
        when(roadNodeMapper.selectList(any())).thenReturn(List.of());

        service.enrich(7L, target);

        assertEquals("ROUTE-AB12", target.getRouteAuditRouteId());
        assertEquals("V43", target.getRouteMapVersion());
        assertEquals(0, target.getRouteDeviationMeters().compareTo(java.math.BigDecimal.valueOf(12.5)));
        assertEquals(LocalDateTime.of(2026, 8, 24, 9, 0), target.getRouteExecutedAt());
        assertNull(target.getCurrentRoadNodeCode(), "无道路节点数据时不匹配");
    }

    @Test
    void nearestActiveNodeWithinThresholdShouldBeReportedAsCurrentRoad() {
        VehicleAdminDetailResponse target = detail(1L, "121.100000", "31.920000");
        when(routeAuditMapper.selectOne(any())).thenReturn(null);

        RoadNodeEntity near = new RoadNodeEntity();
        near.setNodeCode("N-001");
        near.setParkId(1L);
        near.setStatus("ACTIVE");
        near.setCoordLng(new BigDecimal("121.100010"));
        near.setCoordLat(new BigDecimal("31.920008"));
        RoadNodeEntity far = new RoadNodeEntity();
        far.setNodeCode("N-002");
        far.setParkId(1L);
        far.setStatus("ACTIVE");
        far.setCoordLng(new BigDecimal("121.500000"));
        far.setCoordLat(new BigDecimal("32.200000"));
        when(roadNodeMapper.selectList(any())).thenReturn(List.of(near, far));

        service.enrich(7L, target);

        assertEquals("N-001", target.getCurrentRoadNodeCode());
        assertNull(target.getRouteAuditRouteId(), "无审计记录保持为空");
    }

    @Test
    void missingPositionOrParkShouldSkipRoadMatching() {
        VehicleAdminDetailResponse noPosition = detail(1L, null, null);
        when(roadNodeMapper.selectList(any())).thenReturn(List.of());

        service.enrich(7L, noPosition);

        assertNull(noPosition.getCurrentRoadNodeCode());
    }
}