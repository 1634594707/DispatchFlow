package com.fsd.dispatch.geo;

import com.fsd.dispatch.config.ParkPilotProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ParkGeoTransformService {

    private static final double METERS_PER_DEGREE_LAT = 111_320d;

    private final ParkPilotProperties parkPilotProperties;

    /** Phase 4：仿射变换系数，懒加载。null 表示未配置/计算失败，回退到单点锚点。 */
    private volatile AffineCoefficients affineCoefficients;

    public ParkGeoTransformService(ParkPilotProperties parkPilotProperties) {
        this.parkPilotProperties = parkPilotProperties;
    }

    public boolean isEnabled() {
        ParkPilotProperties.GeoConfig geo = parkPilotProperties.getGeo();
        return geo != null && geo.isEnabled();
    }

    public Optional<GeoPoint> toGcj02(BigDecimal parkX, BigDecimal parkY) {
        if (!isEnabled() || parkX == null || parkY == null) {
            return Optional.empty();
        }
        // Phase 4：优先使用 4 点仿射变换（如已配置参考点）
        AffineCoefficients affine = resolveAffine();
        if (affine != null) {
            double lng = affine.a * parkX.doubleValue() + affine.b * parkY.doubleValue() + affine.c;
            double lat = affine.d * parkX.doubleValue() + affine.e * parkY.doubleValue() + affine.f;
            return Optional.of(new GeoPoint(scale(lng), scale(lat)));
        }
        // 回退：单点锚点 + 均匀缩放
        ParkPilotProperties.GeoConfig geo = parkPilotProperties.getGeo();
        int mapWidth = safeDimension(parkPilotProperties.getWidth(), 1200);
        int mapHeight = safeDimension(parkPilotProperties.getHeight(), 800);
        int widthMeters = safeDimension(geo.getParkWidthMeters(), 2400);
        int heightMeters = safeDimension(geo.getParkHeightMeters(), 1600);

        double anchorLng = geo.getAnchorLng().doubleValue();
        double anchorLat = geo.getAnchorLat().doubleValue();
        double metersPerPxX = widthMeters / (double) mapWidth;
        double metersPerPxY = heightMeters / (double) mapHeight;
        double deltaEastMeters = (parkX.doubleValue() - mapWidth / 2d) * metersPerPxX;
        double deltaNorthMeters = (mapHeight / 2d - parkY.doubleValue()) * metersPerPxY;
        double lng = anchorLng + deltaEastMeters / metersPerDegreeLng(anchorLat);
        double lat = anchorLat + deltaNorthMeters / METERS_PER_DEGREE_LAT;
        return Optional.of(new GeoPoint(scale(lng), scale(lat)));
    }

    private static int safeDimension(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private double metersPerDegreeLng(double latitudeDegrees) {
        return METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(latitudeDegrees));
    }

    /** GCJ-02 → 园区 schematic x/y（与 {@link #toGcj02} 互逆）。 */
    public Optional<ParkPoint> fromGcj02(BigDecimal longitude, BigDecimal latitude) {
        if (!isEnabled() || longitude == null || latitude == null) {
            return Optional.empty();
        }
        // Phase 4：优先使用仿射逆变换
        AffineCoefficients affine = resolveAffine();
        if (affine != null) {
            double det = affine.a * affine.e - affine.b * affine.d;
            if (Math.abs(det) < 1e-12) {
                // 奇异矩阵，回退
            } else {
                double lng = longitude.doubleValue();
                double lat = latitude.doubleValue();
                double x = (affine.e * (lng - affine.c) - affine.b * (lat - affine.f)) / det;
                double y = (affine.a * (lat - affine.f) - affine.d * (lng - affine.c)) / det;
                return Optional.of(new ParkPoint(scale(x), scale(y)));
            }
        }
        // 回退：单点锚点逆变换
        ParkPilotProperties.GeoConfig geo = parkPilotProperties.getGeo();
        int mapWidth = safeDimension(parkPilotProperties.getWidth(), 1200);
        int mapHeight = safeDimension(parkPilotProperties.getHeight(), 800);
        int widthMeters = safeDimension(geo.getParkWidthMeters(), 2400);
        int heightMeters = safeDimension(geo.getParkHeightMeters(), 1600);

        double anchorLng = geo.getAnchorLng().doubleValue();
        double anchorLat = geo.getAnchorLat().doubleValue();
        double metersPerPxX = widthMeters / (double) mapWidth;
        double metersPerPxY = heightMeters / (double) mapHeight;
        double deltaEastMeters = (longitude.doubleValue() - anchorLng) * metersPerDegreeLng(anchorLat);
        double deltaNorthMeters = (latitude.doubleValue() - anchorLat) * METERS_PER_DEGREE_LAT;
        double parkX = mapWidth / 2d + deltaEastMeters / metersPerPxX;
        double parkY = mapHeight / 2d - deltaNorthMeters / metersPerPxY;
        return Optional.of(new ParkPoint(scale(parkX), scale(parkY)));
    }

    /**
     * Phase 4：从配置的参考点计算最小二乘仿射变换系数。
     * 需至少 3 个非共线点。返回 null 表示不可用（回退到单点锚点）。
     */
    private AffineCoefficients resolveAffine() {
        if (affineCoefficients != null) {
            return affineCoefficients;
        }
        ParkPilotProperties.GeoConfig geo = parkPilotProperties.getGeo();
        if (geo == null || geo.getReferencePoints() == null) {
            return null;
        }
        List<ParkPilotProperties.GeoReferencePoint> refs = geo.getReferencePoints();
        if (refs.size() < 3) {
            return null;
        }
        // 过滤无效点
        List<double[]> valid = new java.util.ArrayList<>();
        for (ParkPilotProperties.GeoReferencePoint ref : refs) {
            if (ref.getX() != null && ref.getY() != null && ref.getLng() != null && ref.getLat() != null) {
                valid.add(new double[]{
                        ref.getX().doubleValue(), ref.getY().doubleValue(),
                        ref.getLng().doubleValue(), ref.getLat().doubleValue()});
            }
        }
        if (valid.size() < 3) {
            return null;
        }
        // 最小二乘：lng = a*x + b*y + c, lat = d*x + e*y + f
        // 正规方程 A^T A * params = A^T b
        double sxx = 0, sxy = 0, sx = 0, syy = 0, sy = 0, n = valid.size();
        double sxLng = 0, syLng = 0, sLng = 0;
        double sxLat = 0, syLat = 0, sLat = 0;
        for (double[] p : valid) {
            double x = p[0], y = p[1], lng = p[2], lat = p[3];
            sxx += x * x; sxy += x * y; sx += x;
            syy += y * y; sy += y;
            sxLng += x * lng; syLng += y * lng; sLng += lng;
            sxLat += x * lat; syLat += y * lat; sLat += lat;
        }
        // A^T A = [[sxx, sxy, sx], [sxy, syy, sy], [sx, sy, n]]
        // 解 3x3 线性方程组（Cramer 法则）
        double[] lngParams = solve3x3(sxx, sxy, sx, sxy, syy, sy, sx, sy, n, sxLng, syLng, sLng);
        double[] latParams = solve3x3(sxx, sxy, sx, sxy, syy, sy, sx, sy, n, sxLat, syLat, sLat);
        if (lngParams == null || latParams == null) {
            return null;
        }
        affineCoefficients = new AffineCoefficients(
                lngParams[0], lngParams[1], lngParams[2],
                latParams[0], latParams[1], latParams[2]);
        return affineCoefficients;
    }

    /** 解 3x3 线性方程组 M * [p0, p1, p2]^T = rhs，返回 null 表示奇异。 */
    private static double[] solve3x3(double m00, double m01, double m02,
                                      double m10, double m11, double m12,
                                      double m20, double m21, double m22,
                                      double r0, double r1, double r2) {
        double det = m00 * (m11 * m22 - m12 * m21)
                - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
        if (Math.abs(det) < 1e-15) {
            return null;
        }
        double p0 = (r0 * (m11 * m22 - m12 * m21)
                - m01 * (r1 * m22 - m12 * r2)
                + m02 * (r1 * m21 - m11 * r2)) / det;
        double p1 = (m00 * (r1 * m22 - m12 * r2)
                - r0 * (m10 * m22 - m12 * r2)
                + m02 * (m10 * r2 - r1 * m20)) / det;
        double p2 = (m00 * (m11 * r2 - r1 * m21)
                - m01 * (m10 * r2 - r1 * m20)
                + r0 * (m10 * m21 - m11 * m20)) / det;
        return new double[]{p0, p1, p2};
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    public record GeoPoint(BigDecimal longitude, BigDecimal latitude) {
    }

    public record ParkPoint(BigDecimal x, BigDecimal y) {
    }

    /** Phase 4：仿射变换系数 lng = a*x + b*y + c, lat = d*x + e*y + f */
    private record AffineCoefficients(double a, double b, double c,
                                       double d, double e, double f) {
    }
}
