package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RoadSegmentMapper;
import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.service.ParkStationService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 路网图进程内缓存（§M5 图缓存）。这条是 M2E 扩范围的前置：节点从 79 涨到 400-600 时，
 * 每台候选车两次全量加载会把派单 P95 从 277 ms 拖到秒级。
 */
class ParkRoadGraphCacheTest {

    private final RoadNodeMapper nodeMapper = mock(RoadNodeMapper.class);
    private final RoadSegmentMapper segmentMapper = mock(RoadSegmentMapper.class);
    private final ParkStationService stationService = mock(ParkStationService.class);

    private RoadNodeEntity node(String code) {
        RoadNodeEntity entity = new RoadNodeEntity();
        entity.setNodeCode(code);
        entity.setParkId(1L);
        entity.setStatus("ACTIVE");
        entity.setCoordX(new BigDecimal(code.equals("A") ? 0 : 10));
        entity.setCoordY(new BigDecimal("0"));
        return entity;
    }

    private ParkRoutePlannerServiceImpl service(long ttlMillis) {
        ParkPilotProperties props = new ParkPilotProperties();
        props.getRoutePlan().setGraphCacheTtlMs(ttlMillis);
        when(nodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(node("A"), node("B")));
        when(segmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        return new ParkRoutePlannerServiceImpl(props, nodeMapper, segmentMapper, stationService);
    }

    @Test
    void warmCacheServesEverySubsequentLookupWithoutTouchingTheDatabase() {
        ParkRoutePlannerServiceImpl service = service(60_000L);

        for (int i = 0; i < 12; i++) {
            assertEquals(2, service.loadGraph(1L).nodes().size());
        }

        verify(nodeMapper, times(1)).selectList(any(Wrapper.class));
        verify(segmentMapper, times(1)).selectList(any(Wrapper.class));
    }

    @Test
    void zeroTtlKeepsTheOldAlwaysReloadBehaviour() {
        ParkRoutePlannerServiceImpl service = service(0L);

        for (int i = 0; i < 3; i++) {
            service.loadGraph(1L);
        }

        verify(nodeMapper, times(3)).selectList(any(Wrapper.class));
    }

    @Test
    void separateParksGetSeparateEntries() {
        ParkRoutePlannerServiceImpl service = service(60_000L);

        service.loadGraph(1L);
        service.loadGraph(2L);

        verify(nodeMapper, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void invalidationForcesTheNextPlanningCallToReload() {
        ParkRoutePlannerServiceImpl service = service(60_000L);
        service.loadGraph(1L);

        service.invalidateGraphCache();
        service.loadGraph(1L);

        verify(nodeMapper, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void versionFingerprintDescribesTheGraphInUse() {
        ParkRoutePlannerServiceImpl service = service(60_000L);

        String version = service.graphVersion(1L);

        assertNotNull(version);
        assertTrue(version.contains("nodes=2"), version);
        // 版本只读缓存，不应再触发一次建图
        verify(nodeMapper, times(1)).selectList(any(Wrapper.class));
    }

    @Test
    void yamlFallbackIsUsedWhenTheParkHasNoDbNodes() {
        ParkPilotProperties props = new ParkPilotProperties();
        props.getRoutePlan().setGraphCacheTtlMs(60_000L);
        when(nodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(segmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        ParkRoutePlannerServiceImpl service = new ParkRoutePlannerServiceImpl(props, nodeMapper, segmentMapper, stationService);

        ParkRoadGraph first = service.loadGraph(1L);
        ParkRoadGraph second = service.loadGraph(1L);

        verify(nodeMapper, times(1)).selectList(any(Wrapper.class));
        verify(segmentMapper, never()).selectList(any(Wrapper.class));
        assertEquals(first, second);
    }
}
