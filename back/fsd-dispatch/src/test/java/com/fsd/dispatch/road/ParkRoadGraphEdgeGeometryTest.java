package com.fsd.dispatch.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.service.impl.ParkRoutePlannerServiceImpl;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * W2-e：边几何必须进图、并体现在派单路线上。
 *
 * <p>守的是这条：现役 633 条 ACTIVE 边里 275 条是 700–2,740 m 的长边，而 A* 只给节点序列 ⇒
 * 不铺中间形状点就是在这些边上拉直线，即"路线不沿实路"的成因。
 */
class ParkRoadGraphEdgeGeometryTest {

    private static RoadNodeEntity node(String code, double x, double y, double lng, double lat) {
        RoadNodeEntity entity = new RoadNodeEntity();
        entity.setNodeCode(code);
        entity.setParkId(1L);
        entity.setCoordX(BigDecimal.valueOf(x));
        entity.setCoordY(BigDecimal.valueOf(y));
        entity.setCoordLng(BigDecimal.valueOf(lng));
        entity.setCoordLat(BigDecimal.valueOf(lat));
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        return entity;
    }

    private static RoadSegmentEntity segment(String from, String to, String polyline) {
        RoadSegmentEntity entity = new RoadSegmentEntity();
        entity.setParkId(1L);
        entity.setFromNodeCode(from);
        entity.setToNodeCode(to);
        entity.setStatus("ACTIVE");
        entity.setPolylineGeojson(polyline);
        entity.setDeleted(0);
        return entity;
    }

    /**
     * N1(x100,y100 @121.0800,31.9600) → N2(x500,y500 @121.0810,31.9610)：
     * 两个轴都变化，中间顶点在 OSM way 上打了两个。末点与 N2 重合 ⇒ 应当被跳过。
     */
    private static final String BENDY = "[[121.0800,31.9600],[121.0803,31.9601],"
            + "[121.0806,31.9602],[121.0810,31.9610]]";

    private static List<RoadNodeEntity> nodes() {
        List<RoadNodeEntity> out = new ArrayList<>();
        out.add(node("N1", 100, 100, 121.0800, 31.9600));
        out.add(node("N2", 500, 500, 121.0810, 31.9610));
        return out;
    }

    @Test
    void bareCoordinateArrayPolylineIsLoadedForBothDirections() {
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes(), List.of(segment("N1", "N2", BENDY)));

        List<double[]> forward = graph.edgeGeometry("N1", "N2");
        assertEquals(4, forward.size());
        assertEquals(121.0803, forward.get(1)[0], 1e-9);
        assertEquals(121.0806, forward.get(2)[0], 1e-9);
        assertEquals(31.9610, forward.get(3)[1], 1e-9);

        List<double[]> backward = graph.edgeGeometry("N2", "N1");
        assertEquals(4, backward.size());
        // 反向必须是倒过来的链，否则拼接时线会从终点跳回起点
        assertEquals(forward.get(0)[0], backward.get(3)[0], 1e-9);
        assertEquals(forward.get(1)[0], backward.get(2)[0], 1e-9);
        assertEquals(forward.get(3)[0], backward.get(0)[0], 1e-9);
    }

    @Test
    void fullGeoJsonObjectFormIsAlsoAccepted() {
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes(), List.of(segment("N1", "N2",
                "{\"type\":\"LineString\",\"coordinates\":[[121.0800,31.9600],[121.0805,31.9605],"
                        + "[121.0810,31.9610]]}")));

        assertEquals(3, graph.edgeGeometry("N1", "N2").size());
    }

    @Test
    void dirtyOrMissingPolylineDegradesToNodeOnlyInsteadOfFailingTheWholeGraph() {
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes(), List.of(
                segment("N1", "N2", "not json at all"),
                segment("N1", "N2", ""),
                segment("N1", "N2", "[[121.0800]]")));

        assertEquals(2, graph.nodes().size(), "脏几何不该炸掉建图");
        assertEquals(1, graph.adjacency().get("N1").size(), "脏几何不该连带把边也删掉");
        assertTrue(graph.edgeGeometry("N1", "N2").isEmpty(), "脏数据不该产出几何");
    }

    @Test
    void blockedSegmentLosesItsGeometryAlongWithTheEdge() {
        RoadSegmentEntity blocked = segment("N1", "N2", BENDY);
        blocked.setAccessState("BLOCKED");
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes(), List.of(blocked));

        assertTrue(graph.adjacency().get("N1").isEmpty());
        assertTrue(graph.edgeGeometry("N1", "N2").isEmpty(), "禁行边不该还能被铺进路线");
    }

    @Test
    void dispatchedRouteCarriesEdgeShapePointsWithBothCoordinateSystems() {
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes(), List.of(segment("N1", "N2", BENDY)));
        // buildRouteFromNodePath 只吃图，不碰 mapper/station ⇒ 依赖留空即可
        ParkRoutePlannerServiceImpl planner = new ParkRoutePlannerServiceImpl(null, null, null, null);

        List<ParkPointResponse> route = planner.buildRouteFromNodePath(graph,
                BigDecimal.valueOf(100), BigDecimal.valueOf(100),
                BigDecimal.valueOf(500), BigDecimal.valueOf(500),
                List.of("N1", "N2"));

        // START + N1 + 2 个中间形状点 + N2 + END（折线末点与 N2 重合，应被跳过）
        assertEquals(6, route.size(), "长边上的两个中间顶点必须出现在路线里");
        for (ParkPointResponse point : route) {
            boolean hasGcj = point.getLongitude() != null && point.getLatitude() != null;
            boolean hasSchematic = point.getX() != null && point.getY() != null;
            assertTrue(hasGcj || hasSchematic, "点 " + point.getCode() + " 两套坐标都没有");
        }

        ParkPointResponse firstShape = route.get(2);
        ParkPointResponse secondShape = route.get(3);
        assertEquals(121.0803, firstShape.getLongitude().doubleValue(), 1e-9);
        assertEquals(121.0806, secondShape.getLongitude().doubleValue(), 1e-9);
        // 示意映射是无旋转、逐轴仿射 ⇒ 线性内插就是精确解：x=100+0.3*400, y=100+0.1*400
        assertNotNull(firstShape.getX());
        assertNotNull(firstShape.getY());
        assertEquals(220D, firstShape.getX().doubleValue(), 0.01);
        assertEquals(140D, firstShape.getY().doubleValue(), 0.01);
        // 形状点必须同时带 GPS：留空会让 pathLength 在同一条路线里于"米/像素"之间来回跳
        assertNotNull(secondShape.getLatitude());
        assertEquals(340D, secondShape.getX().doubleValue(), 0.01);
        assertEquals(180D, secondShape.getY().doubleValue(), 0.01);

        assertNull(route.get(0).getLongitude(), "START 是否补 GPS 由调用方决定，这里保持原语义");
        assertEquals("N2", route.get(4).getCode());
    }

    @Test
    void twoPointEdgeStillProducesNodeOnlyRouteBecauseOsmHasNoIntermediateVertices() {
        ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes(), List.of(
                segment("N1", "N2", "[[121.0800,31.9600],[121.0810,31.9610]]")));
        ParkRoutePlannerServiceImpl planner = new ParkRoutePlannerServiceImpl(null, null, null, null);

        List<ParkPointResponse> route = planner.buildRouteFromNodePath(graph,
                BigDecimal.valueOf(100), BigDecimal.valueOf(100),
                BigDecimal.valueOf(500), BigDecimal.valueOf(500),
                List.of("N1", "N2"));

        assertEquals(4, route.size(), "OSM 本来没打中间点的边，不该凭空造形状点");
    }
}
