package com.fsd.dispatch.geo;

import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.vo.GeoTransformResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段九 9.1 地图精度回归测试。
 *
 * <p>验证 13 个叠石桥 ZJF 试点站点 GCJ-02 经纬度经
 * {@link CoordinateTransformService#parkToGcj02(BigDecimal, BigDecimal)}
 * 与 {@link CoordinateTransformService#gcj02ToPark(BigDecimal, BigDecimal)}
 * 往返后，haversine 偏差小于 20 米。
 *
 * <p>参考数据来源：{@code front/src/maps/zjfStationAnchors.ts}。
 * 园区配置与 {@code ZJF_PILOT_GEO} 一致：anchor(121.080354, 31.961977)、
 * parkWidthMeters=1570、parkHeightMeters=470、width=1200、height=800。
 */
class MapAccuracyRegressionTest {

    /** 验收阈值（米）。 */
    private static final double MAX_DEVIATION_METERS = 20.0;

    /** 地球半径（米），与 GeoPolygonUtils.haversineMeters 保持一致。 */
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    /** 13 个 ZJF 试点站点锚点（GCJ-02），来源 front/src/maps/zjfStationAnchors.ts。 */
    private static final String[] STATION_CODES = {
            "ZJF-PICK-01",
            "ZJF-PICK-02",
            "ZJF-DROP-01",
            "ZJF-DROP-02",
            "ZJF-DROP-03",
            "ZJF-DROP-04",
            "ZJF-EXPRESS-01",
            "ZJF-IDLE-01",
            "ZJF-CHG-01",
            "ZJF-CHG-02",
            "ZJF-CHG-03",
            "ZJF-CHG-04",
            "ZJF-CHG-05",
    };

    private static final double[] STATION_LNG = {
            121.074453, 121.072610, 121.079762, 121.087005, 121.074367,
            121.083893, 121.073200, 121.080055, 121.080069, 121.079780,
            121.072610, 121.074442, 121.084334,
    };

    private static final double[] STATION_LAT = {
            31.960396, 31.960726, 31.963627, 31.961780, 31.963548,
            31.962833, 31.963800, 31.961922, 31.961850, 31.963518,
            31.963700, 31.960671, 31.962890,
    };

    private CoordinateTransformService service;

    @BeforeEach
    void setUp() {
        ParkPilotProperties properties = new ParkPilotProperties();
        properties.setWidth(1200);
        properties.setHeight(800);
        ParkPilotProperties.GeoConfig geo = properties.getGeo();
        geo.setEnabled(true);
        geo.setAnchorLng(new BigDecimal("121.080354"));
        geo.setAnchorLat(new BigDecimal("31.961977"));
        geo.setParkWidthMeters(1570);
        geo.setParkHeightMeters(470);

        ParkGeoTransformService geoTransformService = new ParkGeoTransformService(properties);
        service = new CoordinateTransformService(properties, geoTransformService, null);
    }

    @Test
    void allZjfStationsShouldRoundTripWithin20Meters() {
        assertNotNull(service, "CoordinateTransformService must be initialized");

        List<String> summary = new ArrayList<>();
        double maxDeviation = 0d;
        String maxDeviationCode = "";
        int failures = 0;

        for (int i = 0; i < STATION_CODES.length; i++) {
            String code = STATION_CODES[i];
            BigDecimal lng = BigDecimal.valueOf(STATION_LNG[i]);
            BigDecimal lat = BigDecimal.valueOf(STATION_LAT[i]);

            // (lng, lat) → (parkX, parkY)
            var parkOpt = service.gcj02ToPark(lng, lat);
            assertTrue(parkOpt.isPresent(), "gcj02ToPark must return value for " + code);
            BigDecimal parkX = parkOpt.get().getParkX();
            BigDecimal parkY = parkOpt.get().getParkY();

            // (parkX, parkY) → (lng', lat')
            var backOpt = service.parkToGcj02(parkX, parkY);
            assertTrue(backOpt.isPresent(), "parkToGcj02 must return value for " + code);
            GeoTransformResponse back = backOpt.get();

            double deviation = haversineMeters(
                    lng.doubleValue(), lat.doubleValue(),
                    back.getLongitude().doubleValue(), back.getLatitude().doubleValue());

            summary.add(String.format("  %-16s lng=%.6f lat=%.6f → parkX=%.4f parkY=%.4f → lng'=%.6f lat'=%.6f | deviation=%.3f m %s",
                    code, STATION_LNG[i], STATION_LAT[i],
                    parkX.doubleValue(), parkY.doubleValue(),
                    back.getLongitude().doubleValue(), back.getLatitude().doubleValue(),
                    deviation,
                    deviation < MAX_DEVIATION_METERS ? "OK" : "FAIL"));

            if (deviation > maxDeviation) {
                maxDeviation = deviation;
                maxDeviationCode = code;
            }
            if (deviation >= MAX_DEVIATION_METERS) {
                failures++;
            }
            assertTrue(deviation < MAX_DEVIATION_METERS,
                    "Station " + code + " deviation " + deviation + "m exceeds threshold " + MAX_DEVIATION_METERS + "m");
        }

        // 打印调试汇总表
        System.out.println("=== 阶段九 9.1 地图精度回归测试 — 13 ZJF 站点往返偏差汇总 ===");
        System.out.println("  阈值: < " + MAX_DEVIATION_METERS + " m  |  共 " + STATION_CODES.length + " 个站点");
        summary.forEach(System.out::println);
        System.out.printf("  最大偏差: %.3f m (%s)%n", maxDeviation, maxDeviationCode);
        System.out.printf("  失败站点数: %d%n", failures);
        System.out.println("===================================================");
    }

    /**
     * 标准 haversine 大圆距离公式，返回米。
     * 与 {@code GeoPolygonUtils.haversineMeters} 算法一致（R=6371000m）。
     */
    static double haversineMeters(double lng1, double lat1, double lng2, double lat2) {
        double lat1r = Math.toRadians(lat1);
        double lat2r = Math.toRadians(lat2);
        double dLat = lat2r - lat1r;
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1r) * Math.cos(lat2r) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }
}
