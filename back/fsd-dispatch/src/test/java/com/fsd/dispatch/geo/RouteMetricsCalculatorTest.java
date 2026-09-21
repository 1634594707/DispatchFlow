package com.fsd.dispatch.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RoadSegmentMapper;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 路线指标的 ETA 口径（M4「ETA 落后端」）。
 *
 * <p>要守住的不是"算得出数"，而是**别再拿一个写死的常数冒充路段限速**：
 * 现役 124 条边的长度加权调和平均是实测 13.19 km/h，而按条数取算术平均是 15.76、
 * 原代码写死 15 —— 三者差约 18%，全落在 ETA 上。
 */
@ExtendWith(MockitoExtension.class)
class RouteMetricsCalculatorTest {

    private static final double FALLBACK_KMH = 13.19D;

    @Mock
    private RoadSegmentMapper roadSegmentMapper;

    @Mock
    private RoadNodeMapper roadNodeMapper;

    private RouteMetricsCalculator calculator() {
        return new RouteMetricsCalculator(roadSegmentMapper, roadNodeMapper);
    }

    /** 同一条经线上取三个点，保证两段长度近似相等，权重才好手算。 */
    private static GeoPoint pt(double lng) {
        return new GeoPoint(BigDecimal.valueOf(lng), BigDecimal.valueOf(31.96));
    }

    private static RoadSegmentEntity segment(String from, String to, int speedKmh) {
        RoadSegmentEntity seg = new RoadSegmentEntity();
        seg.setFromNodeCode(from);
        seg.setToNodeCode(to);
        seg.setSpeedLimitKmh(speedKmh);
        return seg;
    }

    private static RoadNodeEntity node(String code, double lng) {
        RoadNodeEntity entity = new RoadNodeEntity();
        entity.setNodeCode(code);
        entity.setCoordLng(BigDecimal.valueOf(lng));
        entity.setCoordLat(BigDecimal.valueOf(31.96));
        return entity;
    }

    @Test
    void etaShouldWeavePerSegmentLimitsInsteadOfOneConstant() {
        // A->B 限速 20、B->C 限速 10，两段等长：正确 ETA 必须比"全程按 15"明显更长
        List<GeoPoint> polyline = List.of(pt(121.000), pt(121.010), pt(121.020));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                segment("A", "B", 20), segment("B", "C", 10)));
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                node("A", 121.000), node("B", 121.010), node("C", 121.020)));

        long eta = calculator().compute(1L, polyline, List.of("A", "B", "C"), null, null, null)
                .estimatedTravelSeconds();

        double ab = GeoPolygonUtils.haversineMeters(pt(121.000), pt(121.010));
        double bc = GeoPolygonUtils.haversineMeters(pt(121.010), pt(121.020));
        long expected = (long) Math.ceil(ab / (20 * 1000.0 / 3600.0) + bc / (10 * 1000.0 / 3600.0));
        assertEquals(expected, eta, "逐段加权后的 ETA 必须等于 Σ(段长/段限速)");

        long oneConstant = (long) ((ab + bc) / (15 * 1000.0 / 3600.0));
        assertTrue(eta > oneConstant,
                () -> String.format(Locale.ROOT, "慢段没被计入：加权 %d s vs 按 15 算 %d s", eta, oneConstant));
    }

    @Test
    void uncoveredRouteShouldFallBackToMeasuredNetworkSpeed() {
        List<GeoPoint> polyline = List.of(pt(121.000), pt(121.010));
        double length = GeoPolygonUtils.haversineMeters(pt(121.000), pt(121.010));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        long eta = calculator().compute(1L, polyline, List.of(), null, null, null).estimatedTravelSeconds();

        assertEquals((long) Math.ceil(length / (FALLBACK_KMH * 1000.0 / 3600.0)), eta,
                "回退值必须是实测 13.19，不是凭空的 15：这两者的差就是 ETA 的系统偏差");
    }

    @Test
    void partialNodePathShouldOnlyPriceTheMeasuredPart() {
        // 规划器只给出起终点吸附节点时：中间段没有实测限速可查，整条按回退速度算（但不得凭空加时间）
        List<GeoPoint> polyline = List.of(pt(121.000), pt(121.010), pt(121.020));
        double total = GeoPolygonUtils.haversineMeters(pt(121.000), pt(121.010))
                + GeoPolygonUtils.haversineMeters(pt(121.010), pt(121.020));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(segment("A", "C", 20)));
        when(roadNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                node("A", 121.000), node("C", 121.020)));

        long eta = calculator().compute(1L, polyline, List.of("A", "C"), null, null, null).estimatedTravelSeconds();

        // A->C 这条"边"实际跨了两段折线：按节点间实测长度 2235 m 计价 20 km/h，其余按 13.19 补
        double measured = GeoPolygonUtils.haversineMeters(pt(121.000), pt(121.020));
        double remainder = total - measured;
        assertEquals((long) Math.ceil(measured / (20 * 1000.0 / 3600.0)
                + remainder / (FALLBACK_KMH * 1000.0 / 3600.0)), eta);
    }

    @Test
    void missingParkIdShouldNotQueryRoadDataAtAll() {
        List<GeoPoint> polyline = List.of(pt(121.000), pt(121.010));

        long eta = calculator().compute(null, polyline, List.of("A", "B"), null, null, null).estimatedTravelSeconds();

        verifyNoInteractions(roadSegmentMapper);
        verifyNoInteractions(roadNodeMapper);
        assertTrue(eta > 0, "parkId 缺失也要给出回退 ETA，不能返回 0 让上层把空值当'马上到'");
    }

    @Test
    void riskQueryMustScopeParkToBothBranches() {
        List<GeoPoint> polyline = List.of(pt(121.000), pt(121.010));
        when(roadSegmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        calculator().compute(1L, polyline, List.of("A", "B"), null, null, null);

        ArgumentCaptor<Wrapper<RoadSegmentEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(roadSegmentMapper, org.mockito.Mockito.atLeastOnce()).selectList(captor.capture());
        String sql = captor.getAllValues().get(captor.getAllValues().size() - 1)
                .getTargetSql().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertTrue(sql.contains("park_id"), "风险点查询必须带园区过滤：" + sql);
        assertTrue(sql.indexOf("or to_node_code") > sql.indexOf("and ("),
                "OR 必须被括在一个 AND 分支里，否则 park_id 只罩住第一支，会捞到别的园区：" + sql);
    }
}
