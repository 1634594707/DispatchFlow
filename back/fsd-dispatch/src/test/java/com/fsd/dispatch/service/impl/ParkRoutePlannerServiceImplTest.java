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

    // ==================== 阶段九 9.3 路径规划回归测试 ====================

    /**
     * 9.3 跨分区路径可达性：3 个节点 R1/R2/R3 在一条直线上，模拟跨 3 个配送分区。
     * 验证从 R1 邻近区域到 R3 邻近区域可达，且路径包含预期节点数（START + R1 + R2 + R3 + END = 5）。
     */
    @Test
    void crossPartitionRouteShouldBeReachable() {
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                node("R1", 100, 120),
                node("R2", 400, 120),
                node("R3", 700, 120)));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                segment("R1", "R2"),
                segment("R2", "R3")));

        BigDecimal startX = BigDecimal.valueOf(105);
        BigDecimal startY = BigDecimal.valueOf(125);
        BigDecimal endX = BigDecimal.valueOf(695);
        BigDecimal endY = BigDecimal.valueOf(125);

        assertTrue(planner.isReachable(1L, startX, startY, endX, endY));
        List<com.fsd.dispatch.vo.ParkPointResponse> route =
                planner.buildRoute(1L, startX, startY, endX, endY);
        // START + R1 + R2 + R3 + END = 5 个路径点
        assertEquals(5, route.size());
        assertEquals("START", route.get(0).getCode());
        assertEquals("R1", route.get(1).getCode());
        assertEquals("R2", route.get(2).getCode());
        assertEquals("R3", route.get(3).getCode());
        assertEquals("END", route.get(4).getCode());
    }

    /**
     * 9.3 障碍绕行仍可达：4 个节点组成正方形环路（R1→R2→R3→R4→R1）。
     * R1 与 R3 为对角节点，路径可经 R2 或 R4 绕行到达。
     * 验证从 R1 邻近到 R3 邻近可达，且路径节点序列为 [R1, R2 或 R4, R3]（共 5 个路径点）。
     */
    @Test
    void detourAroundObstacleShouldStillReach() {
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                node("R1", 100, 100),
                node("R2", 300, 100),
                node("R3", 300, 300),
                node("R4", 100, 300)));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                segment("R1", "R2"),
                segment("R2", "R3"),
                segment("R3", "R4"),
                segment("R4", "R1")));

        BigDecimal startX = BigDecimal.valueOf(105);
        BigDecimal startY = BigDecimal.valueOf(105);
        BigDecimal endX = BigDecimal.valueOf(295);
        BigDecimal endY = BigDecimal.valueOf(295);

        assertTrue(planner.isReachable(1L, startX, startY, endX, endY));
        List<com.fsd.dispatch.vo.ParkPointResponse> route =
                planner.buildRoute(1L, startX, startY, endX, endY);
        // START + R1 + (R2 或 R4) + R3 + END = 5 个路径点
        assertEquals(5, route.size());
        assertEquals("START", route.get(0).getCode());
        assertEquals("R1", route.get(1).getCode());
        assertEquals("R3", route.get(3).getCode());
        assertEquals("END", route.get(4).getCode());
        // 中间节点为 R2 或 R4（Dijkstra 选其一，正方形两路径等价）
        String midNode = route.get(2).getCode();
        assertTrue("R2".equals(midNode) || "R4".equals(midNode),
                "中间节点应为 R2 或 R4，实际: " + midNode);
    }

    /**
     * 9.3 不可达目的地返回 false：两个互不连通的子图。
     * 子图1：R1-R2；子图2：R3-R4。验证从子图1到子图2 isReachable 返回 false。
     */
    @Test
    void unreachableDestinationShouldReturnEmptyOrFalse() {
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                node("R1", 100, 120),
                node("R2", 220, 120),
                node("R3", 100, 700),
                node("R4", 220, 700)));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                segment("R1", "R2"),
                segment("R3", "R4")));

        // 起点（105,125）邻近 R1（子图1），终点（215,695）邻近 R4（子图2）
        assertFalse(planner.isReachable(1L,
                BigDecimal.valueOf(105), BigDecimal.valueOf(125),
                BigDecimal.valueOf(215), BigDecimal.valueOf(695)));
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
