package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RoadSegmentMapper;
import com.fsd.dispatch.service.ParkStationService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkRoutePlannerServiceImplTest {

    @Mock
    private RoadNodeMapper roadNodeMapper;
    @Mock
    private RoadSegmentMapper roadSegmentMapper;
    @Mock
    private ParkStationService parkStationService;

    private ParkPilotProperties parkPilotProperties;
    private ParkRoutePlannerServiceImpl planner;

    @BeforeEach
    void setUp() {
        parkPilotProperties = new ParkPilotProperties();
        ParkPilotProperties.RoadNodeConfig r1 = new ParkPilotProperties.RoadNodeConfig();
        r1.setCode("R1");
        r1.setX(BigDecimal.valueOf(100));
        r1.setY(BigDecimal.valueOf(120));
        ParkPilotProperties.RoadNodeConfig r2 = new ParkPilotProperties.RoadNodeConfig();
        r2.setCode("R2");
        r2.setX(BigDecimal.valueOf(220));
        r2.setY(BigDecimal.valueOf(120));
        parkPilotProperties.setRoadNodes(List.of(r1, r2));
        ParkPilotProperties.RoadSegmentConfig segment = new ParkPilotProperties.RoadSegmentConfig();
        segment.setFrom("R1");
        segment.setTo("R2");
        parkPilotProperties.setRoadSegments(List.of(segment));

        planner = new ParkRoutePlannerServiceImpl(
                parkPilotProperties, roadNodeMapper, roadSegmentMapper, parkStationService);
    }

    @Test
    void shouldUseDatabaseGraphWhenParkHasRoadNodes() {
        RoadNodeEntity dbR1 = node("R1", 100, 120);
        RoadNodeEntity dbR2 = node("R2", 220, 120);
        RoadNodeEntity dbR3 = node("R3", 420, 120);
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dbR1, dbR2, dbR3));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                segment("R1", "R2"),
                segment("R2", "R3")));

        assertTrue(planner.isReachable(1L, BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(410), BigDecimal.valueOf(125)));
        assertEquals(5, planner.buildRoute(1L, BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(410), BigDecimal.valueOf(125)).size());
        verify(roadNodeMapper, atLeastOnce()).selectList(any(Wrapper.class));
    }

    @Test
    void shouldFallbackToYamlWhenDatabaseHasNoRoadNodes() {
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertTrue(planner.isReachable(1L, BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(210), BigDecimal.valueOf(125)));
        verify(roadSegmentMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void shouldReportUnreachableWhenGraphIsDisconnected() {
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                node("R1", 100, 120),
                node("R2", 220, 120),
                node("R19", 100, 700)));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(segment("R1", "R2")));

        assertFalse(planner.isReachable(1L, BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(105), BigDecimal.valueOf(695)));
    }

    private static RoadNodeEntity node(String code, int x, int y) {
        RoadNodeEntity entity = new RoadNodeEntity();
        entity.setNodeCode(code);
        entity.setCoordX(BigDecimal.valueOf(x));
        entity.setCoordY(BigDecimal.valueOf(y));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private static RoadSegmentEntity segment(String from, String to) {
        RoadSegmentEntity entity = new RoadSegmentEntity();
        entity.setFromNodeCode(from);
        entity.setToNodeCode(to);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
