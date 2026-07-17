package com.fsd.common.geo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Phase 5 任务 5.1：WGS-84 ↔ GCJ-02 坐标转换工具。
 * <p>
 * 真实车辆 GPS 模块上报的原始坐标为 WGS-84，需在入库前转换为 GCJ-02，
 * 否则在高德地图上会存在约 50-500 米的偏移。
 * <p>
 * 算法基于国家标准 GCJ-02 加密偏移公式。outOfChina 判断为境外点直接返回原值。
 */
public final class Wgs84Gcj02Converter {

    private static final double A = 6378245.0D;
    private static final double EE = 0.00669342162296594323D;
    private static final double PI = Math.PI;

    private Wgs84Gcj02Converter() {
    }

    /** WGS-84 → GCJ-02。返回 [longitude, latitude]。 */
    public static double[] wgs84ToGcj02(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double dLat = transformLat(lng - 105.0D, lat - 35.0D);
        double dLng = transformLng(lng - 105.0D, lat - 35.0D);
        double radLat = lat / 180.0D * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0D) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0D) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{lng + dLng, lat + dLat};
    }

    /** GCJ-02 → WGS-84（近似逆变换，精度约 1-2 米）。返回 [longitude, latitude]。 */
    public static double[] gcj02ToWgs84(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double[] gcj = wgs84ToGcj02(lng, lat);
        double dLat = gcj[1] - lat;
        double dLng = gcj[0] - lng;
        return new double[]{lng - dLng, lat - dLat};
    }

    /** 便捷重载：BigDecimal 入参，返回 [longitude, latitude]。null 入参返回原值。 */
    public static BigDecimal[] wgs84ToGcj02(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            return new BigDecimal[]{lng, lat};
        }
        double[] result = wgs84ToGcj02(lng.doubleValue(), lat.doubleValue());
        return new BigDecimal[]{
                BigDecimal.valueOf(result[0]).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(result[1]).setScale(6, RoundingMode.HALF_UP)
        };
    }

    private static boolean outOfChina(double lng, double lat) {
        return lng < 72.004D || lng > 137.8347D || lat < 0.8293D || lat > 55.8271D;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0D + 2.0D * x + 3.0D * y + 0.2D * y * y
                + 0.1D * x * y + 0.2D * Math.sqrt(Math.abs(x));
        ret += (20.0D * Math.sin(6.0D * x * PI) + 20.0D * Math.sin(2.0D * x * PI)) * 2.0D / 3.0D;
        ret += (20.0D * Math.sin(y * PI) + 40.0D * Math.sin(y / 3.0D * PI)) * 2.0D / 3.0D;
        ret += (160.0D * Math.sin(y / 12.0D * PI) + 320.0D * Math.sin(y * PI / 30.0D)) * 2.0D / 3.0D;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0D + x + 2.0D * y + 0.1D * x * x
                + 0.1D * x * y + 0.1D * Math.sqrt(Math.abs(x));
        ret += (20.0D * Math.sin(6.0D * x * PI) + 20.0D * Math.sin(2.0D * x * PI)) * 2.0D / 3.0D;
        ret += (20.0D * Math.sin(x * PI) + 40.0D * Math.sin(x / 3.0D * PI)) * 2.0D / 3.0D;
        ret += (150.0D * Math.sin(x / 12.0D * PI) + 300.0D * Math.sin(x / 30.0D * PI)) * 2.0D / 3.0D;
        return ret;
    }
}
