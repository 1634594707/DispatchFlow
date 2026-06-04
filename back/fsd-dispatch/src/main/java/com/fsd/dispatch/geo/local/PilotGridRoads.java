package com.fsd.dispatch.geo.local;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.util.List;

/**
 * L1 简化道路走廊（GCJ-02），仅作 OSM 真源缺失或子图不连通时的降级补充。
 * <p>正常部署应依赖 {@code data/pilot_osm_geo.json}；本类走廊在 OSM 加载成功时
 * 仅合并「不穿建筑」的线段，避免三套路网并行成为主路径来源（V4-S3）。
 */
final class PilotGridRoads {

    private PilotGridRoads() {
    }

    static List<List<GeoPoint>> corridors() {
        return List.of(
                line(121.075160, 31.961977, 121.088022, 31.961825),
                line(121.080354, 31.961977, 121.084000, 31.961977),
                line(121.084000, 31.961977, 121.088022, 31.961825));
    }

    private static List<GeoPoint> line(double lng1, double lat1, double lng2, double lat2) {
        return List.of(g(lng1, lat1), g(lng2, lat2));
    }

    private static GeoPoint g(double lng, double lat) {
        return LocalPilotRoadGraphService.g(lng, lat);
    }
}
