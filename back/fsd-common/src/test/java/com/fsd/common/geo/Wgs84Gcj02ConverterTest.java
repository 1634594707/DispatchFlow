package com.fsd.common.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Phase 5 任务 5.1：验证 WGS-84 → GCJ-02 转换公式正确性。
 * <p>
 * 叠石桥家纺城试点坐标约 (121.074, 31.960)，转换后偏移应在 50-500 米之间
 * （中国境内 GCJ-02 加密偏移量典型范围）。
 */
class Wgs84Gcj02ConverterTest {

    private static final double ZJF_WGS_LNG = 121.0740;
    private static final double ZJF_WGS_LAT = 31.9600;

    @Test
    void wgs84ToGcj02ShouldProduceOffsetForChinaMainland() {
        double[] gcj = Wgs84Gcj02Converter.wgs84ToGcj02(ZJF_WGS_LNG, ZJF_WGS_LAT);
        // GCJ-02 与 WGS-84 在中国大陆的偏移约 0.001-0.01 度（约 50-1000 米）
        double dLng = Math.abs(gcj[0] - ZJF_WGS_LNG);
        double dLat = Math.abs(gcj[1] - ZJF_WGS_LAT);
        assertTrue(dLng > 0.0005 && dLng < 0.02,
                "经度偏移应在 0.0005-0.02 度之间，实际: " + dLng);
        assertTrue(dLat > 0.0005 && dLat < 0.02,
                "纬度偏移应在 0.0005-0.02 度之间，实际: " + dLat);
    }

    @Test
    void wgs84ToGcj02ShouldReturnOriginalForOutOfChina() {
        // 纽约坐标 (-74.0, 40.7) 不在中国境内，应原值返回
        double[] result = Wgs84Gcj02Converter.wgs84ToGcj02(-74.0, 40.7);
        assertEquals(-74.0, result[0], 1e-9);
        assertEquals(40.7, result[1], 1e-9);
    }

    @Test
    void gcj02ToWgs84ShouldApproximatelyInvertWgs84ToGcj02() {
        double[] gcj = Wgs84Gcj02Converter.wgs84ToGcj02(ZJF_WGS_LNG, ZJF_WGS_LAT);
        double[] wgs = Wgs84Gcj02Converter.gcj02ToWgs84(gcj[0], gcj[1]);
        // 近似逆变换精度约 1-2 米（0.00002 度）
        assertEquals(ZJF_WGS_LNG, wgs[0], 0.00002,
                "GCJ-02 逆变换回 WGS-84 经度误差应 < 2 米");
        assertEquals(ZJF_WGS_LAT, wgs[1], 0.00002,
                "GCJ-02 逆变换回 WGS-84 纬度误差应 < 2 米");
    }

    @Test
    void bigDecimalOverloadShouldPreserveNullInputs() {
        BigDecimal[] result = Wgs84Gcj02Converter.wgs84ToGcj02(null, null);
        assertEquals(null, result[0]);
        assertEquals(null, result[1]);
    }

    @Test
    void bigDecimalOverloadShouldMatchPrimitiveOverload() {
        BigDecimal[] bdResult = Wgs84Gcj02Converter.wgs84ToGcj02(
                new BigDecimal("121.0740"), new BigDecimal("31.9600"));
        double[] primResult = Wgs84Gcj02Converter.wgs84ToGcj02(121.0740, 31.9600);
        assertEquals(primResult[0], bdResult[0].doubleValue(), 1e-6);
        assertEquals(primResult[1], bdResult[1].doubleValue(), 1e-6);
    }
}
