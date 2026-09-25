package com.fsd.dispatch.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.GeoServiceProperties;
import com.fsd.dispatch.entity.ParkGeofenceEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.mapper.ParkGeofenceMapper;
import com.fsd.dispatch.mapper.StationMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GEO-PG Java 侧接线测试（GEO-PG）。
 *
 * <p><b>这里不重复证明 PostGIS 与 Java 手算的等价性</b>——那是
 * {@code geo-py/tests/test_java_parity.py} 的职责：它在每张真实围栏的外接矩形上
 * 跑 60x60 网格逐点比对 {@code ST_Contains} 与 {@code GeoPolygonUtils.contains}。
 * 本测试只证明<b>接线行为</b>：开关关时不发请求、开关开但服务挂时能降级、降级结果正确。
 */
@ExtendWith(MockitoExtension.class)
class GeoQueryServiceTest {

    /** 真实数据：V37 ZJF-ZONE-CORE-SOUTH 的十顶点多边形。 */
    private static final String SOUTH_CORE_POLYGON =
            "[[121.071812,31.960126],[121.073405,31.959762],[121.075820,31.959683],"
            + "[121.077914,31.959821],[121.079352,31.960235],[121.079588,31.961045],"
            + "[121.079152,31.961698],[121.077213,31.961912],[121.074893,31.961856],"
            + "[121.072610,31.961624]]";

    /** 该围栏质心，PostGIS 判定为内部。 */
    private static final BigDecimal INSIDE_LNG = new BigDecimal("121.076176");
    private static final BigDecimal INSIDE_LAT = new BigDecimal("31.960776");

    @Mock
    private ParkGeofenceMapper geofenceMapper;
    @Mock
    private StationMapper stationMapper;
    @Mock
    private GeoServiceClient client;

    private GeoServiceProperties properties;
    private GeoQueryService service;

    @BeforeEach
    void setUp() {
        properties = new GeoServiceProperties();
        service = new GeoQueryService(client, properties, geofenceMapper, stationMapper, new ObjectMapper());
    }

    @Test
    void 默认关闭时不发起任何HTTP请求() {
        assertFalse(properties.isEnabled(), "默认值必须是关闭——生产行为不得被这次改造静默改变");

        stubFence(SOUTH_CORE_POLYGON);
        when(client.fenceContains(any(), any(), any())).thenReturn(java.util.Optional.empty());

        assertTrue(service.containsInAnyFence(1L, INSIDE_LNG, INSIDE_LAT));
        // 走的是本地多边形判定，不是远端
        verify(geofenceMapper).selectList(any(Wrapper.class));
    }

    @Test
    void 服务不可用时降级结果与手算一致() {
        stubFence(SOUTH_CORE_POLYGON);
        when(client.fenceContains(any(), any(), any())).thenReturn(java.util.Optional.empty());

        assertTrue(service.containsInAnyFence(1L, INSIDE_LNG, INSIDE_LAT), "质心应在围栏内");
        assertFalse(service.containsInAnyFence(1L, INSIDE_LNG, INSIDE_LAT.add(new BigDecimal("0.05"))),
                "北移 5 公里应在围栏外");
    }

    @Test
    void 服务可用时直接采用远端结果且不查库() {
        when(client.fenceContains(any(), any(), any()))
                .thenReturn(java.util.Optional.of(List.of("ZJF-ZONE-CORE-SOUTH")));

        assertTrue(service.containsInAnyFence(1L, INSIDE_LNG, INSIDE_LAT));
        verify(geofenceMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void 远端返回空围栏列表时判定为外部() {
        when(client.fenceContains(any(), any(), any())).thenReturn(java.util.Optional.of(List.of()));
        assertFalse(service.containsInAnyFence(1L, INSIDE_LNG, INSIDE_LAT));
        verify(geofenceMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void 站点召回降级按距离升序且只保留充电桩() {
        when(client.nearbyStations(any(), any(Double.TYPE), any(Double.TYPE),
                org.mockito.ArgumentMatchers.anyInt(), any(Double.TYPE), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(java.util.Optional.empty());
        when(stationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                station("ZJF-CHG-04", "待命区充电4", new BigDecimal("121.076200"), new BigDecimal("31.960800")),
                station("ZJF-CHG-02", "代发仓充电2", new BigDecimal("121.077500"), new BigDecimal("31.961500")),
                station("ZJF-PICK-01", "取货点1", new BigDecimal("121.076300"), new BigDecimal("31.960900"))));

        List<GeoServiceClient.StationHit> hits =
                service.nearestStations(1L, INSIDE_LNG.doubleValue(), INSIDE_LAT.doubleValue(), 5, true);

        assertEquals(2, hits.size(), "三个站点里只有两个是充电桩");
        assertTrue(hits.stream().allMatch(GeoServiceClient.StationHit::charging));
        assertTrue(hits.get(0).distanceMeters() <= hits.get(1).distanceMeters(), "必须按距离升序");
        assertEquals("ZJF-CHG-04", hits.get(0).stationCode());
    }

    @Test
    void 超出默认半径的站点即使存在也不被召回() {
        // ZJF-CHG-01 真实坐标距查询点约 5.4km，超过 GeoServiceProperties.defaultRadiusM=5000
        when(client.nearbyStations(any(), any(Double.TYPE), any(Double.TYPE),
                org.mockito.ArgumentMatchers.anyInt(), any(Double.TYPE), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(java.util.Optional.empty());
        when(stationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                station("ZJF-CHG-01", "待命区充电站", new BigDecimal("121.061800"), new BigDecimal("31.912200"))));

        assertTrue(service.nearestStations(1L, INSIDE_LNG.doubleValue(), INSIDE_LAT.doubleValue(), 5, true).isEmpty(),
                "半径裁剪必须生效，否则返充会择优到几公里外的桩");
    }

    @Test
    void 无坐标站点在降级路径中被跳过而不是抛异常() {
        when(client.nearbyStations(any(), any(Double.TYPE), any(Double.TYPE),
                org.mockito.ArgumentMatchers.anyInt(), any(Double.TYPE), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(java.util.Optional.empty());
        when(stationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                station("V04-NO-GEO", "仅有平面坐标的旧站点", null, null),
                station("ZJF-CHG-04", "有经纬度", new BigDecimal("121.076200"), new BigDecimal("31.960800"))));

        List<GeoServiceClient.StationHit> hits =
                service.nearestStations(1L, INSIDE_LNG.doubleValue(), INSIDE_LAT.doubleValue(), 5, false);

        assertEquals(1, hits.size());
        assertEquals("ZJF-CHG-04", hits.get(0).stationCode());
    }

    @Test
    void 节点吸附在服务不可用时返回null而不是伪造结果() {
        when(client.nearestRoadNode(any(), any(Double.TYPE), any(Double.TYPE), any(Double.TYPE)))
                .thenReturn(java.util.Optional.empty());
        // 刻意不查库：内存图吸附由寻路侧负责，这里重复实现会产生第二份会漂移的口径
        assertNull(service.nearestRoadNodeCode(1L, INSIDE_LNG.doubleValue(), INSIDE_LAT.doubleValue(), 300D));
        verify(geofenceMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void 围栏多边形损坏时按无围栏处理而不是抛出() {
        stubFence("{不是合法 JSON");
        when(client.fenceContains(any(), any(), any())).thenReturn(java.util.Optional.empty());
        assertFalse(service.containsInAnyFence(1L, INSIDE_LNG, INSIDE_LAT));
    }

    private void stubFence(String polygonJson) {
        ParkGeofenceEntity fence = new ParkGeofenceEntity();
        fence.setParkId(1L);
        fence.setFenceCode("ZJF-ZONE-CORE-SOUTH");
        fence.setStatus("ACTIVE");
        fence.setPolygonJson(polygonJson);
        when(geofenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(fence));
    }

    private static StationEntity station(String code, String name, BigDecimal lng, BigDecimal lat) {
        StationEntity entity = new StationEntity();
        entity.setParkId(1L);
        entity.setStationCode(code);
        entity.setStationName(name);
        entity.setStationType("GENERAL");
        entity.setStatus("ACTIVE");
        entity.setCoordLng(lng);
        entity.setCoordLat(lat);
        return entity;
    }
}
